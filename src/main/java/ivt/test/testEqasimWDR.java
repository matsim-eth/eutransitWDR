package ivt.test;

import org.eqasim.core.simulation.analysis.EqasimAnalysisModule;
import org.eqasim.core.simulation.mode_choice.EqasimModeChoiceModule;
import org.eqasim.ile_de_france.IDFConfigurator;
import org.eqasim.ile_de_france.mode_choice.IDFModeChoiceModule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup.RoutingAlgorithmType;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.withinday.replanning.identifiers.tools.LinkReplanningMap;
import org.matsim.withinday.trafficmonitoring.EarliestLinkExitTimeProvider;
import org.matsim.contribs.discrete_mode_choice.modules.DiscreteModeChoiceModule;
import org.matsim.contribs.discrete_mode_choice.replanning.time_interpreter.TimeInterpreterModule;

import wdrConfigurators.WDR_IDFConfigurator;

public class testEqasimWDR {

		static public void main(String[] args) throws ConfigurationException {
			CommandLine cmd = new CommandLine.Builder(args) //
					.requireOptions("config-path") //
					.allowPrefixes("mode-choice-parameter", "cost-parameter") //
					.build();

			Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path"), WDR_IDFConfigurator.getConfigGroups());
			cmd.applyConfiguration(config);

			Scenario scenario = ScenarioUtils.createScenario(config);
			WDR_IDFConfigurator.configureScenario(scenario);
			/*Sept. 21 - Currently the WithinDayTravelTimeModule throws an error if the routing algorithm is not Dijkstra (see line 38 of said class). 
			However, eqasim uses FastAStarLandmarks by default. (see line 67 of org.eqasim.core.scenario.config.GenerateConfig)
			Here, I try to reset the routing algorithim back to Djikstra, to avoid the error from WithiDayTravelTimeModule*/
			//TODO Test to see if Withinday stuff works with FastAStarLandmarks
			//TODO BE AWARE THAT EQASIM MIGHT BE SLOW OR WEIRD WITH DJIKSTRA!!
			config.controler().setRoutingAlgorithmType(RoutingAlgorithmType.Dijkstra);
			ScenarioUtils.loadScenario(scenario);

			Controler controller = new Controler(scenario);
			WDR_IDFConfigurator.configureController(controller);
			controller.addOverridingModule(new DiscreteModeChoiceModule());
			controller.addOverridingModule(new EqasimAnalysisModule());
			controller.addOverridingModule(new EqasimModeChoiceModule());
			controller.addOverridingModule(new IDFModeChoiceModule(cmd));
			//TODO caution..this not only adds WDR modules but also configures scoring! ....so does that mean agents score now, too? ANSWER: Unlikely. eqasim gets rid of scoring. 
			//ANSWER CONTINUED: If it did, shouldn't matter, eqasim ignores scores. BUT eqasim also only keeps one plan...so hopefully eqasim also makes sure to always keep the eqasim-made plan
			//and not that some weird background traditional replanning puts in other plans....but shouldn't, since....the DiscreteModeChoiceModule should insure the traditional replanning
			//and scoring processes are not used. NEVERTHELESS, should eventually make a test to catch any such "silent" errors. 
			ExampleWithinDayController.configure(controller);
			
			
			//ATTEMPTING TO MAKE LinkReplanningMap WORK CORRECTLY AKA FIND THE AGENTS LEAVING LINKS
			//LinkReplanningMap wasn't working because EarliestLinkExitTimeProvider.getEarliestLinkExitTimesPerTimeStep kept returning null. 
			//Thus trying to ensure LinkReplanningMap is properly "registered" as an event handler! - cvl Nov. 2021 COMMENT: that alone did not work
			//Next, trying to register LinkReplanningMap as a "SimulationListner". That doesn't exist under that name, so trying "MobsimListnerBinding" COMMENT: that did not work
			//Next, trying to bind EarliestLinkExitTimeProvider, since LinkReplanningMap uses that to get its map of agents...COMMENT: also not working. Boo. 
			//TODO make LinkReplanningMap and EarliestLinkExitTimeProvider work again! Since I think the Guice-y nature of MATSim and eqasim is probably to blame for this malfunctioning
			/*controller.addOverridingModule(new AbstractModule() {
				
				@Override
				public void install() {
					
					this.addEventHandlerBinding().to(LinkReplanningMap.class);
					bind(LinkReplanningMap.class).asEagerSingleton();
					
					this.addMobsimListenerBinding().to(LinkReplanningMap.class);
					bind(LinkReplanningMap.class).asEagerSingleton();
					
					this.addEventHandlerBinding().to(EarliestLinkExitTimeProvider.class);
					bind(EarliestLinkExitTimeProvider.class).asEagerSingleton();
				}
			});
			*/
			
			
			controller.run();
		}

}
