package wdrConfigurators;

import java.util.Arrays;
import java.util.List;

import org.eqasim.core.components.EqasimComponentsModule;
import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.core.components.traffic.EqasimTrafficQSimModule;
import org.eqasim.core.components.transit.EqasimTransitModule;
import org.eqasim.core.components.transit.EqasimTransitQSimModule;
import org.eqasim.core.simulation.EqasimConfigurator;
import org.eqasim.core.simulation.calibration.CalibrationConfigGroup;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contribs.discrete_mode_choice.modules.DiscreteModeChoiceModule;
import org.matsim.contribs.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.mobsim.qsim.AbstractQSimModule;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutilityFactory;
import org.matsim.core.scoring.functions.OnlyTravelTimeDependentScoringFunctionFactory;
import org.matsim.households.Household;
import org.matsim.withinday.controller.WithinDayConfigGroup;
import org.matsim.withinday.controller.WithinDayModule;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import ivt.test.ExampleWithinDayControllerEqaSim;

public class WDReqasimConfigurator extends EqasimConfigurator{
	

	static public ConfigGroup[] getWDReqasimConfigGroups() {
		return new ConfigGroup[] { //
				new SwissRailRaptorConfigGroup(), //
				new EqasimConfigGroup(), //
				new DiscreteModeChoiceConfigGroup(), //
				new CalibrationConfigGroup(),
				new WithinDayConfigGroup()};
	}
	
	static public List<AbstractModule> getModules() {
		return Arrays.asList( //
				new SwissRailRaptorModule(), //
				new EqasimTransitModule(), //
				new DiscreteModeChoiceModule(), //
				new EqasimComponentsModule() //
				//new WithinDayModule()//
		);
	}

	static public List<AbstractQSimModule> getQSimModules() {
		return Arrays.asList( //
				new EqasimTransitQSimModule(), //
				new EqasimTrafficQSimModule() //
				//new WithinDayQSimModule()
		);
	}
	
	//TODO merge this configure(Controler controller) with the configureController from EqasimConfigurator and then use it in ExampleWithinDayControllerEqaSim - cvl June 2021

	static public void configureControllerWDR(Controler controller) {
		// factored out for testing. kai, jun'16
		controller.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				install(new WithinDayModule());
				addControlerListenerBinding().to(ExampleWithinDayControllerEqaSim.class);

				addTravelDisutilityFactoryBinding(TransportMode.car).toInstance(new OnlyTimeDependentTravelDisutilityFactory());

				// Use a Scoring Function that only scores the travel times:
				// (yy but why? kai,  jun'16)
				bindScoringFunctionFactory().toInstance(new OnlyTravelTimeDependentScoringFunctionFactory());
			}
		});
	}
	
	static public void configureController(Controler controller) {
		for (AbstractModule module : getModules()) {
			controller.addOverridingModule(module);
		}

		for (AbstractQSimModule module : getQSimModules()) {
			controller.addOverridingQSimModule(module);
		}

		controller.configureQSimComponents(configurator -> {
			EqasimTransitQSimModule.configure(configurator, controller.getConfig());
		});
	}

	static public void configureScenario(Scenario scenario) {
	}

	static public void adjustScenario(Scenario scenario) {
		for (Household household : scenario.getHouseholds().getHouseholds().values()) {
			for (Id<Person> memberId : household.getMemberIds()) {
				Person person = scenario.getPopulation().getPersons().get(memberId);

				if (person != null) {
					copyAttribute(household, person, "bikeAvailability");
					copyAttribute(household, person, "spRegion");
				}
			}
		}
	}

	static protected void copyAttribute(Household household, Person person, String attribute) {
		if (household.getAttributes().getAsMap().containsKey(attribute)) {
			person.getAttributes().putAttribute(attribute, household.getAttributes().getAttribute(attribute));
		}
	}
}
