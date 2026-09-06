package com.aiavatar.alterego.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Constitution Principle VII enforcement: the backend's four named layers
 * (boundary, application, domain, infrastructure) form a strict
 * unidirectional dependency graph. Reverse edges are prohibited; the only
 * class permitted outside the four top-level layer packages is the Spring
 * Boot entry point {@code AlterEgoApplication}.
 *
 * <p>Applied to {@code main/java/com.aiavatar.alterego.**} only — test
 * code is excluded by {@link ImportOption.Predefined#DO_NOT_INCLUDE_TESTS}.
 */
class LayerBoundariesTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.aiavatar.alterego");
    }

    @Test
    void fourLayerArchitectureIsRespected() {
        ArchRule rule = layeredArchitecture()
                .consideringAllDependencies()
                .layer("EntryPoint").definedBy("com.aiavatar.alterego")  // AlterEgoApplication only
                .layer("Boundary").definedBy("..boundary..")
                .layer("Application").definedBy("..application..")
                .layer("Domain").definedBy("..domain..")
                .layer("Infrastructure").definedBy("..infrastructure..")

                // Dependency direction:
                //   EntryPoint   -> may touch anything (Spring Boot bootstrap)
                //   boundary    -> application, domain
                //   application -> domain
                //   infrastructure -> application (via ports), domain
                //   domain      -> (nothing internal)
                .whereLayer("Boundary").mayOnlyBeAccessedByLayers("EntryPoint")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Boundary", "Infrastructure", "EntryPoint")
                .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Boundary", "EntryPoint");
        rule.check(classes);
    }
}
