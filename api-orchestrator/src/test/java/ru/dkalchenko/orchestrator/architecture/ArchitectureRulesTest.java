package ru.dkalchenko.orchestrator.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import ru.dkalchenko.orchestrator.ApiOrchestratorApplication;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(
		packagesOf = ApiOrchestratorApplication.class,
		importOptions = ImportOption.DoNotIncludeTests.class
)
class ArchitectureRulesTest {

	@ArchTest
	static final ArchRule no_rest_template =
			noClasses()
					.that().resideInAPackage("ru.dkalchenko.orchestrator..")
					.should().dependOnClassesThat().haveSimpleName("RestTemplate");

	@ArchTest
	static final ArchRule no_web_client_for_outbound =
			noClasses()
					.that().resideInAPackage("ru.dkalchenko.orchestrator..")
					.should().dependOnClassesThat().haveSimpleName("WebClient");

	@ArchTest
	static final ArchRule application_does_not_depend_on_adapters_or_config =
			noClasses()
					.that().resideInAPackage("ru.dkalchenko.orchestrator.application..")
					.should().dependOnClassesThat()
					.resideInAnyPackage(
							"ru.dkalchenko.orchestrator.adapter..",
							"ru.dkalchenko.orchestrator.config..");

	@ArchTest
	static final ArchRule domain_does_not_depend_on_spring_or_feign =
			noClasses()
					.that().resideInAPackage("ru.dkalchenko.orchestrator.domain..")
					.should().dependOnClassesThat()
					.resideInAnyPackage(
							"org.springframework..",
							"feign..");

	@ArchTest
	static final ArchRule inbound_adapters_should_use_application_ports =
			classes()
					.that().resideInAPackage("ru.dkalchenko.orchestrator.adapter.inbound..")
					.should().onlyDependOnClassesThat()
					.resideInAnyPackage(
							"ru.dkalchenko.orchestrator.adapter.inbound..",
							"ru.dkalchenko.orchestrator.application..",
							"ru.dkalchenko.orchestrator.application.port.in..",
							"ru.dkalchenko.orchestrator.api..",
							"ru.dkalchenko.orchestrator.config..",
							"com.fasterxml..",
							"jakarta..",
							"lombok..",
							"org.slf4j..",
							"org.springframework..",
							"reactor..",
							"java..");

	@ArchTest
	static final ArchRule application_should_not_depend_on_downstream_api_models =
			noClasses()
					.that().resideInAPackage("ru.dkalchenko.orchestrator.application..")
					.should().dependOnClassesThat()
					.resideInAnyPackage(
							"ru.dkalchenko.device.api..",
							"ru.dkalchenko.event.api..");
}
