package com.aiavatar.alterego.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Constitution Principle VIII enforcement: the orchestrator MUST NOT
 * branch on provider identity. Implemented as two complementary rules:
 *
 *   1. {@code application.*} must not import any concrete provider class
 *      under {@code infrastructure.provider.{gemini,falai,stub,fallback}.*}.
 *   2. {@code application.*} must not contain the string literals
 *      {@code "gemini"}, {@code "falai"}, {@code "stub"} — using the
 *      {@link com.aiavatar.alterego.domain.model.Provider} enum is the
 *      only sanctioned way to compute telemetry-side provider strings.
 *
 * (FR-2402 / spec 024.)
 */
class NoProviderBranchingTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.aiavatar.alterego");
    }

    @Test
    void applicationLayerMustNotImportConcreteProviderClasses() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "..infrastructure.provider.gemini..",
                        "..infrastructure.provider.falai..",
                        "..infrastructure.provider.stub..",
                        "..infrastructure.provider.fallback..");
        rule.check(classes);
    }
}
