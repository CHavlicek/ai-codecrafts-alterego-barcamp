package com.aiavatar.alterego.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Constitution Principle IX enforcement: the unit tier must run without a
 * Spring context. Asserts no class under {@code test/com.aiavatar.alterego.unit..}
 * imports any {@code org.springframework.boot.test.*} class or carries an
 * {@code @SpringBootTest} annotation.
 *
 * <p>Tests are imported via {@link ImportOption.Predefined#ONLY_INCLUDE_TESTS}.
 */
class UnitTierIsolationTest {

    @Test
    void unitTierMustNotLoadSpringBootTestSupport() {
        JavaClasses unitClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages("com.aiavatar.alterego.unit");

        ArchRule rule = noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.boot.test..",
                        "org.springframework.boot.autoconfigure..",
                        "org.springframework.test..");
        rule.check(unitClasses);
    }
}
