package com.aiavatar.alterego.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Constitution Principle VII enforcement: the {@code domain} layer is
 * pure in-process Java — no Spring, no servlet API, no AWT/ImageIO, no
 * infrastructure. The {@code @Configuration}-bound types live in
 * {@code infrastructure.config}; the JDK Graphics2D and JavaMail
 * adapters live in {@code infrastructure.overlay}/{@code infrastructure.email}.
 */
class DomainPurityTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.aiavatar.alterego");
    }

    @Test
    void domainClassesMustNotDependOnInfrastructureOrFramework() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.servlet..",
                        "javax.imageio..",
                        "javax.mail..",
                        "jakarta.mail..",
                        "..infrastructure..",
                        "..application..",
                        "..boundary..");
        rule.check(classes);
    }
}
