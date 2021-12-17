package ivt.test;
/* *********************************************************************** *
 * project: org.matsim.*
 * ExampleWithinDayController.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutilityFactory;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.functions.OnlyTravelTimeDependentScoringFunctionFactory;
import org.matsim.withinday.controller.WithinDayConfigGroup;
import org.matsim.withinday.controller.WithinDayModule;
import org.matsim.withinday.mobsim.MobsimDataProvider;
import org.matsim.withinday.mobsim.WithinDayEngine;
import org.matsim.withinday.replanning.identifiers.ActivityEndIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.InitialIdentifierImplFactory;
import org.matsim.withinday.replanning.identifiers.LeaveLinkIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringActivityAgentSelector;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringActivityIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringLegAgentSelector;
import org.matsim.withinday.replanning.identifiers.interfaces.DuringLegIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.interfaces.InitialIdentifier;
import org.matsim.withinday.replanning.identifiers.interfaces.InitialIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.tools.ActivityReplanningMap;
import org.matsim.withinday.replanning.identifiers.tools.LinkReplanningMap;
import org.matsim.withinday.replanning.replanners.CurrentLegReplannerFactory;
import org.matsim.withinday.replanning.replanners.InitialReplannerFactory;
import org.matsim.withinday.replanning.replanners.NextLegReplannerFactory;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringActivityReplannerFactory;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringLegReplannerFactory;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayInitialReplannerFactory;

import ivtIdentifiers.ProbabilityFilterFactoryRemovePtAgents;
import ivtIdentifiers.SpecificAgentFilterFactory;
import wdrConfigurators.TestWDRModeConfigurator;
import wdrConfigurators.WDReqasimConfigurator;

import org.eqasim.core.simulation.analysis.EqasimAnalysisModule;
import org.eqasim.core.simulation.mode_choice.EqasimModeChoiceModule;
import org.eqasim.ile_de_france.IDFConfigurator;
import org.eqasim.ile_de_france.mode_choice.IDFModeChoiceModule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * This class should give an example what is needed to run
 * simulations with WithinDayReplanning.
 *
 * The path to a config file is needed as argument to run the
 * simulation.
 * 
 * It should be possible to run this class with
 * "src/test/resources/test/scenarios/berlin/config_withinday.xml"
 * as argument.
 *
 * @author Christoph Dobler
 */
@Singleton
public final class ExampleWithinDayControllerEqaSim implements StartupListener {
	// yyyy I think that for the now existing guice approach this example has too many factories at too many levels. kai, feb'16

	/*
	 * Define the Probability that an Agent uses the
	 * Replanning Strategy. It is possible to assign
	 * multiple Strategies to the Agents.
	 */
	private double pInitialReplanning = 0.5;
	private double pDuringActivityReplanning = 0.2;
	private double pDuringLegReplanning = 0.25;
	
	private Set<Person> specificAgentsToReplan = new HashSet<Person>(); 


	
	private InitialIdentifierFactory initialIdentifierFactory;
	private DuringActivityIdentifierFactory duringActivityIdentifierFactory;
	private DuringLegIdentifierFactory duringLegIdentifierFactory;
	
	private InitialIdentifier initialIdentifier;
	private DuringActivityAgentSelector duringActivityIdentifier;
	private DuringLegAgentSelector duringLegIdentifier;
	
	private WithinDayInitialReplannerFactory initialReplannerFactory;
	private WithinDayDuringActivityReplannerFactory duringActivityReplannerFactory;
	private WithinDayDuringLegReplannerFactory duringLegReplannerFactory;
	
	private ProbabilityFilterFactoryRemovePtAgents initialProbabilityFilterFactory;
	private ProbabilityFilterFactoryRemovePtAgents duringActivityProbabilityFilterFactory;
	private ProbabilityFilterFactoryRemovePtAgents duringLegProbabilityFilterFactory;
	
	private SpecificAgentFilterFactory initialSpecificAgentSelectorFactory;
	private SpecificAgentFilterFactory duringActivitySpecificAgentSelectorFactory;
	private SpecificAgentFilterFactory duringLegSpecificAgentSelectorFactory;
	
	@Inject private Scenario scenario;
	@Inject private Provider<TripRouter> tripRouterProvider;
	@Inject private MobsimDataProvider mobsimDataProvider;
	@Inject private WithinDayEngine withinDayEngine;
	@Inject private ActivityReplanningMap activityReplanningMap;
	@Inject private LinkReplanningMap linkReplanningMap;
	@Inject private LeastCostPathCalculatorFactory pathCalculatorFactory;
	@Inject private Map<String,TravelDisutilityFactory> travelDisutilityFactories;
	@Inject private Map<String,TravelTime> travelTimes;


	/*
	 * ===================================================================
	 * main
	 * ===================================================================
	 */
	public static void main(final String[] args) throws ConfigurationException {
		
		//following code-block might not be necessary anymore, since the new eqasim CommandLine.Builder is being used...cvl may '21
		/*if ((args == null) || (args.length == 0)) {
			System.out.println("No argument given!");
//			System.out.println("Usage: Controler config-file [dtd-file]");
			// the [dtd-file] argument was not honoured when I found this fca451f279bd4c8e3921846597d657614d5a5832 . kai, may'17
			System.out.println("Usage: Controler config-file");
			System.out.println();
			System.exit(-1);
		}
		*/ 
		
		CommandLine cmd = new CommandLine.Builder(args) //
				.requireOptions("config-path") //
				.allowPrefixes("mode-choice-parameter", "cost-parameter") //
				.build();
		
		Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path") , WDReqasimConfigurator.getWDReqasimConfigGroups()) ;
		config.controler().setRoutingAlgorithmType( ControlerConfigGroup.RoutingAlgorithmType.Dijkstra );
		
		//trying to add mode "car_passenger" so Corsica scenario will run with this controller - cvl April 2021
		TestWDRModeConfigurator.configure(config);

		//createScenario(config) and configureScenario are lines from the default idf eqasim run class -cvl May 2021
		//I think they do not do anything are are just place holders in case you DO want to do something,
		// since the method configureScenario(scenario) inside EqasimConfigurator is empty - cvl May 2021
		Scenario scenario = ScenarioUtils.createScenario(config);
		WDReqasimConfigurator.configureScenario(scenario);
		//following line is from ExampleWithinDayController, and was commeted out because createScenario was used. - cvl May 2021
		//Scenario scenario = ScenarioUtils.loadScenario( config) ;
		
		final Controler controller = new Controler(scenario);
		//using the configure(Controler controller) method installs and binds WDR-Specific things to the controller. - cvl May 2021
		//TODO do we need all the things bound with configure(Controler controller)? - cvl May 2021
		//TODO do we need to change these things for our purposes? - cvl May 2021
		//TODO *****how does it differ from IDFConfigurator.configureController(controller)????? - cvl May 2021
		//*****one "installs" and/or "binds" eqasim modules to QSim, the other does the same, but for WDR "modules" - cvl June 2021
		//TODO can I use both, or do they conflict? - cvl May 2021
		//TODO merge eqasimConfigurator.configureController with the method configureControllerWDR? -cvl June 2021
		WDReqasimConfigurator.configureController(controller);

		WDReqasimConfigurator.configureControllerWDR(controller);
		//next overriding modules are from default idf eqasim run class - cvl May 2021
		//TODO are the compatible with WDR? - cvl May 2021
		controller.addOverridingModule(new EqasimAnalysisModule());
		controller.addOverridingModule(new EqasimModeChoiceModule());
		controller.addOverridingModule(new IDFModeChoiceModule(cmd));
		controller.run();
	}
	



	@Override
	public void notifyStartup(StartupEvent event) {
		this.initReplanners(  );
	}
	
	private void initReplanners( ) {
		Network network = this.scenario.getNetwork() ;
		
		TravelTime travelTime = travelTimes.get( TransportMode.car ) ;

		TravelDisutilityFactory travelDisutilityFactory = travelDisutilityFactories.get( TransportMode.car ) ;
		TravelDisutility travelDisutility = travelDisutilityFactory.createTravelDisutility(travelTime ) ;

		LeastCostPathCalculator pathCalculator = pathCalculatorFactory.createPathCalculator(network, travelDisutility, travelTime ) ;
		
		//Specify which specific agents to replan, if you want to replan specific agents - cvl May 2021
		Person agent_a = scenario.getPopulation().getPersons().values().iterator().next(); 
		this.specificAgentsToReplan.add(agent_a);
		
		
		/*
		this.initialIdentifierFactory = new InitialIdentifierImplFactory(this.mobsimDataProvider);
		this.initialProbabilityFilterFactory = new ProbabilityFilterFactoryRemovePtAgents(this.pInitialReplanning, this.mobsimDataProvider);
		this.initialIdentifierFactory.addAgentFilterFactory(this.initialProbabilityFilterFactory);
		this.initialIdentifier = initialIdentifierFactory.createIdentifier();
		this.initialReplannerFactory = new InitialReplannerFactory(this.scenario, this.withinDayEngine, this.tripRouterProvider);
		this.initialReplannerFactory.addIdentifier(this.initialIdentifier);
		this.withinDayEngine.addIntialReplannerFactory(this.initialReplannerFactory);
		*/
		
		this.duringActivityIdentifierFactory = new ActivityEndIdentifierFactory(this.activityReplanningMap);
		//this.duringActivityProbabilityFilterFactory = new ProbabilityFilterFactoryRemovePtAgents(this.pDuringActivityReplanning, this.mobsimDataProvider);
		//this.duringActivityIdentifierFactory.addAgentFilterFactory(this.duringActivityProbabilityFilterFactory);
		this.duringActivitySpecificAgentSelectorFactory = new SpecificAgentFilterFactory(this.specificAgentsToReplan, this.mobsimDataProvider);
		this.duringActivityIdentifierFactory.addAgentFilterFactory(this.duringActivitySpecificAgentSelectorFactory);
		this.duringActivityIdentifier = duringActivityIdentifierFactory.createIdentifier();
		this.duringActivityReplannerFactory = new NextLegReplannerFactory(this.scenario, this.withinDayEngine, this.tripRouterProvider);
		this.duringActivityReplannerFactory.addIdentifier(this.duringActivityIdentifier);
		this.withinDayEngine.addDuringActivityReplannerFactory(this.duringActivityReplannerFactory);
		

		/*
		this.duringLegIdentifierFactory = new LeaveLinkIdentifierFactory(this.linkReplanningMap, this.mobsimDataProvider);
		this.duringLegProbabilityFilterFactory = new ProbabilityFilterFactoryRemovePtAgents(this.pDuringLegReplanning, this.mobsimDataProvider);
		this.duringLegIdentifierFactory.addAgentFilterFactory(this.duringLegProbabilityFilterFactory);
		this.duringLegIdentifier = this.duringLegIdentifierFactory.createIdentifier();
		this.duringLegReplannerFactory = new CurrentLegReplannerFactory(this.scenario, this.withinDayEngine, pathCalculator);
		this.duringLegReplannerFactory.addIdentifier(this.duringLegIdentifier);
		this.withinDayEngine.addDuringLegReplannerFactory(this.duringLegReplannerFactory);
		*/
	}

}
