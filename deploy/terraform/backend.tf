data "aws_iam_policy_document" "ec2_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "backend" {
  name               = "${local.name}-backend"
  assume_role_policy = data.aws_iam_policy_document.ec2_assume.json
}

# SSM Session Manager + patch baseline access — replaces SSH for shell-in.
resource "aws_iam_role_policy_attachment" "ssm_managed" {
  role       = aws_iam_role.backend.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "backend_inline" {
  statement {
    sid       = "ReadArtifacts"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.artifacts.arn}/*"]
  }
  # Both real-provider keys + the fal.ai timeout knob are read on every
  # refresh.sh run regardless of which profile is active. The unused key
  # sits in /etc/aiavatar.env but is ignored by the runtime per FR-1604
  # (blank-key short-circuit).
  statement {
    sid     = "ReadProviderConfig"
    actions = ["ssm:GetParameter"]
    resources = [
      aws_ssm_parameter.gemini_api_key.arn,
      aws_ssm_parameter.fal_ai_api_key.arn,
      aws_ssm_parameter.fal_ai_end_to_end_timeout_ms.arn,
    ]
  }
  # 027: read the SMTP credentials from the shared mailbox in the
  # ai-closing-letter namespace. ARNs are operator-overridable via
  # var.smtp_{username,password}_ssm_arn. Decrypt for the SecureString
  # password is covered by the DecryptSsmParameter statement below.
  statement {
    sid     = "ReadSmtpCredentials"
    actions = ["ssm:GetParameter"]
    resources = [
      var.smtp_username_ssm_arn,
      var.smtp_password_ssm_arn,
    ]
  }
  # Required to decrypt SecureString params encrypted under the default aws/ssm KMS key.
  statement {
    sid       = "DecryptSsmParameter"
    actions   = ["kms:Decrypt"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["ssm.${var.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "backend_inline" {
  name   = "${local.name}-backend-inline"
  role   = aws_iam_role.backend.id
  policy = data.aws_iam_policy_document.backend_inline.json
}

resource "aws_iam_instance_profile" "backend" {
  name = "${local.name}-backend"
  role = aws_iam_role.backend.name
}

resource "aws_security_group" "backend" {
  name        = "${local.name}-backend"
  description = "Backend EC2: port 8080 only from CloudFront origin-facing prefix list."
  vpc_id      = data.aws_vpc.default.id

  egress {
    description = "All outbound - S3, SSM, Gemini API."
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_vpc_security_group_ingress_rule" "backend_from_cloudfront" {
  security_group_id = aws_security_group.backend.id
  description       = "CloudFront origin-facing prefix list to port 8080."
  ip_protocol       = "tcp"
  from_port         = 8080
  to_port           = 8080
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront_origin.id
}

resource "aws_instance" "backend" {
  ami                         = data.aws_ssm_parameter.al2023_ami.value
  instance_type               = var.instance_type
  subnet_id                   = data.aws_subnets.default.ids[0]
  vpc_security_group_ids      = [aws_security_group.backend.id]
  iam_instance_profile        = aws_iam_instance_profile.backend.name
  associate_public_ip_address = true

  metadata_options {
    http_tokens = "required" # IMDSv2
  }

  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    artifacts_bucket          = aws_s3_bucket.artifacts.id
    jar_key                   = var.backend_jar_s3_key
    gemini_param_name         = aws_ssm_parameter.gemini_api_key.name
    fal_ai_param_name         = aws_ssm_parameter.fal_ai_api_key.name
    fal_ai_timeout_param_name = aws_ssm_parameter.fal_ai_end_to_end_timeout_ms.name
    # 027: SMTP credentials are cross-namespace (live under
    # /ai-closing-letter/...). Strip the ":parameter" prefix off the ARN to get
    # the path that `aws ssm get-parameter --name` accepts.
    smtp_username_param_name = split(":parameter", var.smtp_username_ssm_arn)[1]
    smtp_password_param_name = split(":parameter", var.smtp_password_ssm_arn)[1]
    email_from_override      = var.email_from_override
    active_profile           = var.active_profile
    region                   = var.region
  })

  # Re-provision on user-data change so the systemd unit / refresh script stay in sync.
  user_data_replace_on_change = true

  tags = {
    Name = "${local.name}-backend"
  }
}

# EIP gives CloudFront a stable origin DNS across instance replacements.
resource "aws_eip" "backend" {
  instance = aws_instance.backend.id
  domain   = "vpc"

  tags = {
    Name = "${local.name}-backend"
  }
}
