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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.contribs.discrete_mode_choice.replanning.time_interpreter.TimeInterpreter.Factory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.ControlerListenerManagerImpl;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.AfterMobsimListener;
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
import org.matsim.withinday.controller.ExecutedPlansServiceImpl;
import org.matsim.withinday.controller.WithinDayConfigGroup;
import org.matsim.withinday.controller.WithinDayModule;
import org.matsim.withinday.mobsim.MobsimDataProvider;
import org.matsim.withinday.mobsim.WithinDayEngine;
import org.matsim.withinday.replanning.identifiers.ActivityEndIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.InitialIdentifierImplFactory;
import org.matsim.withinday.replanning.identifiers.LeaveLinkIdentifierFactory;
import org.matsim.withinday.replanning.identifiers.LegPerformingIdentifierFactory;
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
import ivtReplanners.IvtCurrentLegReplannerChooseModeFactory;
import ivtReplanners.IvtNextLegReplannerFactory;
import ivtReplanners.IvtNextLegReplannerFactoryChooseMode;
import ivtReplanners.IvtNextLegReplannerFactoryChooseModeTest;
import wdrConfigurators.TestWDRModeConfigurator;

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
public final class ExampleWithinDayController implements StartupListener {
	// yyyy I think that for the now existing guice approach this example has too many factories at too many levels. kai, feb'16
	// ja, wayyy to many factories. Although I am not sure how to make it guice-y. cvl, July 2021

	/*
	 * Define the Probability that an Agent uses the
	 * Replanning Strategy. It is possible to assign
	 * multiple Strategies to the Agents.
	 */
	private double pInitialReplanning = 0.5;
	private double pDuringActivityReplanning = 0.2;
	private double pDuringLegReplanning = 1.0;
	
	private Set<Person> specificAgentsToReplanGroup1 = new HashSet<Person>(); 
	private Set<Person> specificAgentsToReplanGroup2 = new HashSet<Person>();

	
	private InitialIdentifierFactory initialIdentifierFactory;
	private DuringActivityIdentifierFactory duringActivitySelectorFactory1;
	private DuringActivityIdentifierFactory duringActivitySelectorFactory2;
	private DuringLegIdentifierFactory duringLegIdentifierFactory;
	
	private InitialIdentifier initialIdentifier;
	private DuringActivityAgentSelector duringActivitySelector1;
	private DuringActivityAgentSelector duringActivitySelector2;
	private DuringLegAgentSelector duringLegIdentifier;
	
	private WithinDayInitialReplannerFactory initialReplannerFactory;
	private WithinDayDuringActivityReplannerFactory duringActivityReplannerFactory;
	private WithinDayDuringLegReplannerFactory duringLegReplannerFactory;
	
	private ProbabilityFilterFactoryRemovePtAgents initialProbabilityFilterFactory;
	private ProbabilityFilterFactoryRemovePtAgents duringActivityProbabilityFilterFactory;
	private ProbabilityFilterFactoryRemovePtAgents duringLegProbabilityFilterFactory;
	
	private SpecificAgentFilterFactory initialSpecificAgentFilterFactory;
	private SpecificAgentFilterFactory duringActivitySpecificAgentFilterFactory1;
	private SpecificAgentFilterFactory duringActivitySpecificAgentFilterFactory2;
	private SpecificAgentFilterFactory duringLegSpecificAgentFilterFactory;
	
	@Inject private Scenario scenario;
	@Inject private Provider<TripRouter> tripRouterProvider;
	@Inject private MobsimDataProvider mobsimDataProvider;
	@Inject private WithinDayEngine withinDayEngine;
	@Inject private ActivityReplanningMap activityReplanningMap;
	@Inject private LinkReplanningMap linkReplanningMap;
	@Inject private LeastCostPathCalculatorFactory pathCalculatorFactory;
	@Inject private Map<String,TravelDisutilityFactory> travelDisutilityFactories;
	@Inject private Map<String,TravelTime> travelTimes;
	@Inject private Factory timeInterpreterFactory;
	@Inject private Map<String, UtilityEstimator> utilityEstimatorsMap;



	/*
	 * ===================================================================
	 * main
	 * ===================================================================
	 */
	public static void main(final String[] args) {
		if ((args == null) || (args.length == 0)) {
			System.out.println("No argument given!");
//			System.out.println("Usage: Controler config-file [dtd-file]");
			// the [dtd-file] argument was not honoured when I found this fca451f279bd4c8e3921846597d657614d5a5832 . kai, may'17
			System.out.println("Usage: Controler config-file");
			System.out.println();
			System.exit(-1);
		} 
		
		Config config = ConfigUtils.loadConfig( args[0] , new WithinDayConfigGroup() ) ;
		config.controler().setRoutingAlgorithmType( ControlerConfigGroup.RoutingAlgorithmType.Dijkstra );
		
		//trying to add mode "car_passenger" so Corsica scenario will run with this controller - cvl April 2021
		TestWDRModeConfigurator.configure(config);

		Scenario scenario = ScenarioUtils.loadScenario( config) ;
		
		final Controler controler = new Controler(scenario);
		configure(controler);
		controler.run();
	}

	static void configure(Controler controler) {
		// factored out for testing. kai, jun'16
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				install(new WithinDayModule());
				addControlerListenerBinding().to(ExampleWithinDayController.class);
				
				addTravelDisutilityFactoryBinding(TransportMode.car).toInstance(new OnlyTimeDependentTravelDisutilityFactory());

				// Use a Scoring Function that only scores the travel times:
				// (yy but why? kai,  jun'16)
				bindScoringFunctionFactory().toInstance(new OnlyTravelTimeDependentScoringFunctionFactory());
			}
		});

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
		
		//TODO Make a class that retrieves the agents to replan from...a csv, a small population file, etc.
		
		//This grabs the first agent in the population 
		//Person agent_a = scenario.getPopulation().getPersons().values().iterator().next(); 
		//this.specificAgentsToReplan.add(agent_a);
		
		//trying to create a list of Agent IDs, retrieve those agents, and add them to the specificAgentsToReplan
		//Id<Person> agentid1 = Id.create(100066, Person.class);
		Id<Person> agentid1 = Id.createPersonId(100066);
		Id<Person> agentid2 = Id.createPersonId(32948);
		Id<Person> agentid3 = Id.createPersonId(259683);
		Id<Person> agentid4 = Id.createPersonId(286767);
		Id<Person> agentid5 = Id.createPersonId(212437);
		
		Id<Person> agentid6 = Id.createPersonId(275144);
		Id<Person> agentid7 = Id.createPersonId(25205);
		Id<Person> agentid8 = Id.createPersonId(317589);
		Id<Person> agentid9 = Id.createPersonId(318968);
		Id<Person> agentid10 = Id.createPersonId(100067);
		
		//Find those persons in the population and add them to the agents to replan
		this.specificAgentsToReplanGroup1.add(scenario.getPopulation().getPersons().get(agentid1));
		this.specificAgentsToReplanGroup1.add(scenario.getPopulation().getPersons().get(agentid2));
		this.specificAgentsToReplanGroup1.add(scenario.getPopulation().getPersons().get(agentid3));
		this.specificAgentsToReplanGroup1.add(scenario.getPopulation().getPersons().get(agentid4));
		this.specificAgentsToReplanGroup1.add(scenario.getPopulation().getPersons().get(agentid5));
		
		this.specificAgentsToReplanGroup2.add(scenario.getPopulation().getPersons().get(agentid6));
		this.specificAgentsToReplanGroup2.add(scenario.getPopulation().getPersons().get(agentid7));
		this.specificAgentsToReplanGroup2.add(scenario.getPopulation().getPersons().get(agentid8));
		this.specificAgentsToReplanGroup2.add(scenario.getPopulation().getPersons().get(agentid9));
		this.specificAgentsToReplanGroup2.add(scenario.getPopulation().getPersons().get(agentid10));
		
		//Replanning Agents at the Start of the Simulation//
		/*
		this.initialIdentifierFactory = new InitialIdentifierImplFactory(this.mobsimDataProvider);
		this.initialProbabilityFilterFactory = new ProbabilityFilterFactoryRemovePtAgents(this.pInitialReplanning, this.mobsimDataProvider);
		this.initialIdentifierFactory.addAgentFilterFactory(this.initialProbabilityFilterFactory);
		this.initialIdentifier = initialIdentifierFactory.createIdentifier();
		this.initialReplannerFactory = new InitialReplannerFactory(this.scenario, this.withinDayEngine, this.tripRouterProvider);
		this.initialReplannerFactory.addIdentifier(this.initialIdentifier);
		this.withinDayEngine.addIntialReplannerFactory(this.initialReplannerFactory);
		*/
		
		
		//Replanning Agents Who are Performing Activities//
		//in this specific case, those who are about to end their activities.....
		
		//Instantiate Selector Factories // Note: currently making two, because there's a silent bug/error when you add two filters to one selector. - cvl Nov. 2021
		this.duringActivitySelectorFactory1 = new ActivityEndIdentifierFactory(this.activityReplanningMap);
		this.duringActivitySelectorFactory2 = new ActivityEndIdentifierFactory(this.activityReplanningMap);
		
		//Instantiate Filter Factories //
		this.duringActivitySpecificAgentFilterFactory1 = new SpecificAgentFilterFactory(this.specificAgentsToReplanGroup1, this.mobsimDataProvider);
		this.duringActivitySelectorFactory1.addAgentFilterFactory(this.duringActivitySpecificAgentFilterFactory1);
		
		// BUG #1: when two filters are added to the selector, the second one does not get used and the replanner is not run! - cvl Nov. 2021
		// WORKAROUND: For now, we just create new selector for each filter and eventually (see further down) add those selectors to the replanner. That seems to work. - cvl Nov. 2021
		this.duringActivitySpecificAgentFilterFactory2 = new SpecificAgentFilterFactory(this.specificAgentsToReplanGroup2, this.mobsimDataProvider);
		this.duringActivitySelectorFactory2.addAgentFilterFactory(this.duringActivitySpecificAgentFilterFactory2);
		
		/*TODO 
		* // Notes on the BUG #1 "can't add two filters to one selector"//
		* A test run implied that only the first filter was being used, although debug showed both filters are added. - cvl Nov. 2021
		* Furthermore, when two filters have been created and added to the selector, the replanner never gets called! Why? - cvl Nov. 2021
		* It seems that the activityReplanningMap might not be being used correctly. I think the problem is it isn't an "eager singleton"? - cvl Nov. 2021 
		* An issue perhaps also with the method "getAgentsToReplan"? 
		* No, activityReplanningMap and withinDayEngine and MobsimDataProvider are all bound as singletons (not always denoted as eager singletons, but still, called by the same mechanisim in Guice, it seems)
		* It seems rather that the problem is somehow something prevents or fails to add agents to the "agents to replan" data containers in one of the for loops...but I am not sure which one, when, or why! - cvl Nov. 2021
		* I suspect the "creatIdentifier" method might also be involved in the "can't add two filters to selector" problem, but it is a hunch, nothing more. - cvl Nov. 2021
		*/
		
		//Instantiate Selectors using Selector Factories // Note: the filters are also created INSIDE the selectors in this step. Because we addeed the filter factories to the selector factories. "identifier" is the "old" term for "selectors". 
		this.duringActivitySelector1 = this.duringActivitySelectorFactory1.createIdentifier(); 
		this.duringActivitySelector2 = this.duringActivitySelectorFactory2.createIdentifier();
		
		//Instantiate Replanner Factories//
		this.duringActivityReplannerFactory = new IvtNextLegReplannerFactoryChooseModeTest(this.scenario, this.withinDayEngine, this.tripRouterProvider, this.timeInterpreterFactory, this.utilityEstimatorsMap);
		
		//Add Selectors to Replanner Factories //
		this.duringActivityReplannerFactory.addIdentifier(this.duringActivitySelector1);
		this.duringActivityReplannerFactory.addIdentifier(this.duringActivitySelector2);
		
		//Add Replanner Factories to the WithinDayEngine// Note: this is a very key step. It is the WithinDayEngine that calls all the replanning methods during the simulation! - cvl Nov. 2021		
		
		//Example// of a replanner that is active all day. Without further limitations inside the replanner, agent selector, or agent filter, this will initiate replanning each time an agent is about to end an activity. - cvl, Nov. 2021
		this.withinDayEngine.addDuringActivityReplannerFactory(this.duringActivityReplannerFactory);
		
		//Example// of how to make a replanner time-dependent: in this case, having the replanner only active between 7am and noon. - cvl Nov. 2021
		//this.withinDayEngine.addTimedDuringActivityReplannerFactory(duringActivityReplannerFactory, 25200, 43200);
		
		/*
		 * BUG #2: If you are trying to select agents to replan based on the criteria "agents are currently performing an activity", 
		 * then if an agent, for whatever reason, ends and starts an activity in the same time step, that agent will NOT be replanned
		 * because it will be removed from the key containers within the activityReplanningMap during that time step,
		 * and thus will NOT be available to be added to the AgentsToReplan....
		 * Fixing this will require modifying the class activityReplanningMap! 
		 * That, in turn, will probably require some "refactoring" and possibly "guicification". 		
		*/
		

		
		//Replanning Agents Who are Travelling, aka Performing a Leg//
		
		/*
		 * // C. Dobler's example of how to set up a en-route (during Leg) replanner //
		this.duringLegIdentifierFactory = new LeaveLinkIdentifierFactory(this.linkReplanningMap, this.mobsimDataProvider);
		this.duringLegProbabilityFilterFactory = new ProbabilityFilterFactoryRemovePtAgents(this.pDuringLegReplanning, this.mobsimDataProvider);
		this.duringLegIdentifierFactory.addAgentFilterFactory(this.duringLegProbabilityFilterFactory);
		this.duringLegIdentifier = this.duringLegIdentifierFactory.createIdentifier();
		this.duringLegReplannerFactory = new CurrentLegReplannerFactory(this.scenario, this.withinDayEngine, pathCalculator);
		this.duringLegReplannerFactory.addIdentifier(this.duringLegIdentifier);
		this.withinDayEngine.addDuringLegReplannerFactory(this.duringLegReplannerFactory);
		*/
		
		
		// Instantiate the Selector Factory // in this case, one that identifies agents currently performing a leg, aka en-route
		this.duringLegIdentifierFactory = new LegPerformingIdentifierFactory(this.linkReplanningMap, this.mobsimDataProvider);
		
		//Instantiate the Filter Factory
		this.duringLegProbabilityFilterFactory = new ProbabilityFilterFactoryRemovePtAgents(this.pDuringLegReplanning, this.mobsimDataProvider);
		
		// Add the Filter Factory to the Selector Factory (thus adding the filter to the selector)
		this.duringLegIdentifierFactory.addAgentFilterFactory(this.duringLegProbabilityFilterFactory);
		
		// Create a Selector using the Selector Factory
		this.duringLegIdentifier = this.duringLegIdentifierFactory.createIdentifier();
		
		// Instantiate the Replanner Factory
		this.duringLegReplannerFactory = new IvtCurrentLegReplannerChooseModeFactory(this.scenario, this.withinDayEngine, this.tripRouterProvider, this.timeInterpreterFactory, this.utilityEstimatorsMap);
		
		// Add the Selector to the Replanner Factory (thus adding the selector to this type of replanner)
		this.duringLegReplannerFactory.addIdentifier(this.duringLegIdentifier);
		
		// Add the Replanner Factory to the WithinDayEngine (thus making this within-day-replanning logic available in Mobsim)
		this.withinDayEngine.addDuringLegReplannerFactory(this.duringLegReplannerFactory);
		
	}

}
