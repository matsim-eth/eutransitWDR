package ivtReplanners;


/* *********************************************************************** *
 * project: org.matsim.*
 * NextLegReplanner.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import org.apache.log4j.Logger;
import org.eqasim.core.simulation.mode_choice.EqasimModeChoiceModule;
import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import org.eqasim.core.simulation.mode_choice.utilities.estimators.PtUtilityEstimator;
import org.eqasim.core.simulation.mode_choice.utilities.estimators.WalkUtilityEstimator;
import org.eqasim.ile_de_france.mode_choice.IDFModeChoiceModule;
import org.eqasim.ile_de_france.mode_choice.utilities.estimators.IDFBikeUtilityEstimator;
import org.eqasim.ile_de_france.mode_choice.utilities.estimators.IDFCarUtilityEstimator;
import org.jfree.util.Log;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.Route;
import org.matsim.contribs.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.DefaultRoutedTripCandidate;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.RoutedTripCandidate;
import org.matsim.contribs.discrete_mode_choice.model.trip_based.candidates.TripCandidate;
import org.matsim.contribs.discrete_mode_choice.model.utilities.MultinomialLogitSelector;
import org.matsim.contribs.discrete_mode_choice.model.utilities.UtilityCandidate;
import org.matsim.contribs.discrete_mode_choice.model.utilities.UtilitySelectorFactory;
import org.matsim.contribs.discrete_mode_choice.replanning.time_interpreter.TimeInterpreter;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.qsim.ActivityEndRescheduler;
import org.matsim.core.mobsim.qsim.agents.WithinDayAgentUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.Facility;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringActivityReplanner;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringLegReplanner;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayReplanner;
import org.matsim.withinday.utils.EditRoutes;
import org.matsim.withinday.utils.EditTrips;

import com.google.inject.Inject;
import com.google.inject.multibindings.MapBinder;

/**
 * The NextLegReplanner can be used while an agent is performing an activity. The
 * replanner creates a new trip from the current activity to the next main activity 
 * in the agent's plan.
 * 
 * In fact this should be renamed to NextTripReplanner. cdobler, apr'14
 */

public class IvtCurrentLegReplannerChooseMode extends WithinDayDuringLegReplanner {

	private static final Logger log = Logger.getLogger(EditTrips.class) ;
	
	private final TripRouter tripRouter;
	private final PopulationFactory populationFactory;
	private Map<String, UtilityEstimator> utilityEstimatorsMap;
	private TimeInterpreter.Factory timeInterpreterFactory;
	private Network network;
	
	
	/*package*/ IvtCurrentLegReplannerChooseMode(Id<WithinDayReplanner> id, Scenario scenario,
			ActivityEndRescheduler internalInterface, TripRouter tripRouter,
			TimeInterpreter.Factory timeInterpreterFactory, Map<String, UtilityEstimator> utilityEstimatorsMap,
			Network network) {
		
		super(id, scenario, internalInterface);
		this.tripRouter = tripRouter;
		this.populationFactory = scenario.getPopulation().getFactory();
		this.timeInterpreterFactory = timeInterpreterFactory;
		this.utilityEstimatorsMap = utilityEstimatorsMap;
		this.network = network;
	}

	/*
	 * Replan Route every time the End of a Link is reached.
	 *
	 * Idea:
	 * - determine current mode
	 * - determine available modes
	 * - for each available mode, create a new Route from the current Location to the Destination
	 * - estimate Utility of each Route
	 * - choose from mode-Routes using a choice model
	 * - merge already passed parts of the current Route with the newly chosen Route
	 */
	@Override
	public boolean doReplanning(MobsimAgent withinDayAgent) {

		Id<Person> agentID = withinDayAgent.getId(); // agentID
		
		Plan executedPlan = WithinDayAgentUtils.getModifiablePlan(withinDayAgent);

		// If we don't have an executed plan
		if (executedPlan == null) return false;
		
		// GET CURRENT LOCATION & ROUTE INFO & DO SOME CHECKS //
		
		// Retrieve the plan element the agent is currently performing
		PlanElement currentPlanElement = WithinDayAgentUtils.getCurrentPlanElement(withinDayAgent);
		
		// This plan element should be an instance of Leg, otherwise the agent is not actually en-route
		if (!(currentPlanElement instanceof Leg)) return false;
		
		// Cast the Plan Element object to a Leg object
		Leg currentLeg = (Leg) currentPlanElement;
		
		// Retrieve the agent's current location by retrieving the Index (within its current Route)
		// of the Link the agent is currently on
		int currentLinkIndex = WithinDayAgentUtils.getCurrentRouteLinkIdIndex(withinDayAgent);

		//Get the current Route from the current Leg
		Route route = currentLeg.getRoute();
		
		//Check if route type is supported
		//TODO currently only NetworkRoutes supported. Support also other types (pt???)?
		if (!(route instanceof NetworkRoute)) {
			Log.warn("route being replanning for agent " + agentID + "is not an instance of NetworkRoute");
			return false;
		}
		
		// Store the currently being used route in a new variable of type NetworkRoute
		NetworkRoute oldRoute = (NetworkRoute) route;
		
		// Create a List that contains all LinkIds of a route, including the Start- and EndLinks.
		List<Id<Link>> oldLinkIds = getRouteLinkIds(oldRoute, agentID);
		
		// Get the Id of the current Link, using its Index within the oldRoute
		Id<Link> currentLinkId = oldLinkIds.get(currentLinkIndex);
		
		// Get the Id of the last link (aka destination link) in the current (aka "old") route 
		Id<Link> toLinkId = route.getEndLinkId();
		
		final Link startLink = network.getLinks().get(currentLinkId);
		final Link endLink = network.getLinks().get(toLinkId);
		
		final Coord startLinkCoord = startLink.getCoord();
		
		
		// GET MODE-RELEVANT AGENT ATTRIBUTES //
		
		String currentMode = currentLeg.getMode(); //mode
		Person agent = scenario.getPopulation().getPersons().get(agentID); //agent
		Boolean hasLicense = PersonUtils.hasLicense(agent); // does agent have a driving license?
		Boolean hasPTSubscription = agent.getAttributes().equals("hasPtSubscription"); // does agent have a pt subscription?
		log.info("our agent with ID " + agentID + " has a pt subscription: " + hasPTSubscription +
				" and has a driver's liscense: " + hasLicense); //for tests, comment out for runs
		
		
		
		// DETERMINE AVAILABLE MODES //
		
		ArrayList<String> modesAvailable = DetermineAvailableModes(agent, hasLicense, currentMode);
		log.info("Agent " + agentID + "has the following modes available: " + modesAvailable);
		
		
		// GET AND TRANSFORM STUFF FOR DISCRETE MODE CHOICE MODULE AND EQASIM METHODS //
		
		// TODO tripRoute.calcRoute needs facilities. We have links. Let's try and get facilities?
		// I want to use tripRoute.calcRoute instead of directly using pathCalculator.findLeastCostPath
		// because tripRoute.calcRoute is Guice-y and can handle multiple modes, and multiple pathCalculators.
		// I want to take advantage of that. 
		// But maybe I'll need to make my own tripRoute.calcRoute in order to avoid artificially creating facilities?
		
 
		
		
		//create some dummy facilities wrapped around our links
		// so tripRoute.calcRoute and the DMCTrip-types can deal with our links.
		// Seems this comes up often since there is already a facility that simply wraps around a link....
		Facility dummyStartFacility = FacilitiesUtils.wrapLink(startLink);
		Facility dummyEndFacility = FacilitiesUtils.wrapLink(endLink);
		
		
		
		// The DMC module and eqasim utility estimators need trips as the type DiscreteModeChoiceTrip
		// Thus, we need to create a dummy start "activity" at the agent's current location
		// and retrieve the activity at the end of its current Leg (well, trip, really...?)
		// TODO what i have coded below might have some very pesky bugs. Beware "pt interaction" activities,
		// or activity-dependent stuff within the DMC and eqasim stuff I haven't spotted yet.
		
		// Create a dummy start Activity representing where the agent is right now. CAUTION: beta version! might be buggy.
		//Note: LegImpl objects cannot be cast to Activity objects. Doing so throws an ClassCastException error. 
		Activity dummyStartActivity = PopulationUtils.createActivityFromCoordAndLinkId("WDRReplanning", startLinkCoord, currentLinkId); //NOT AT ALL SURE THIS WILL WORK
		
		// Retrieve the destination activity of the agent's current leg, which is part of the agent's current trip. CAUTION: should replan the whole rest of the trip! If you want only to replan a stage, you must program something else! (most likely)
		Trip currentTrip = TripStructureUtils.findTripAtPlanElement(currentPlanElement, executedPlan);
		Activity destinationActivity = currentTrip.getDestinationActivity(); 
		
		//DEBUGGING: 
		//Either one of the dummy facilities or our dummy start activity doesn't have coords, which
		//makes the CarPredictor throw an error. So, now there's a few lines trying to figure out which
		//object has no coords. RESULT: Turns out it was the dummyStartActivity, Code changed to make sure coords there.
		/*
		Coord dummyStartFacilityCoords = dummyStartFacility.getCoord();
		Coord dummyEndFacilityCoords = dummyEndFacility.getCoord();
		Coord dummyStartActivityCoords = dummyStartActivity.getCoord();
		Coord destinationActivityCoords = destinationActivity.getCoord();
		log.info("the coords are dsf, def, dsa, da: " + dummyStartFacilityCoords + ", "
		+ dummyEndFacilityCoords + ", " + dummyStartActivityCoords + ", "
		+ destinationActivityCoords);
		
		RESULT: Turns out it was the dummyStartActivity, Code changed to make sure coords there.
		*/
		
		//The DMC and eqasim methods we'll be using also require a departure time in seconds. 
		
		// Retrieve departure time in seconds by setting that to the current simulation time, 
		// because the agent is replanning AS OF NOW, NOT THE BEINGING OF THE THEIR TRIP
		//CAUTION: copying this syntax from the defaul CurrentLegReplanner. Hope this mystery "this.time"
		// truly does grab the simulation time of the SimStep about to be executed! 
		double replanDepartureTimeInSeconds = this.time.seconds();
		
		
		// CREATE NEW ROUTE CANDIDATES (includes being able to choose a new mode if available) //
		
		ArrayList<TripCandidate> availableTripCandidates = CreateTripCandidates (agent, modesAvailable,
				executedPlan, dummyStartFacility, dummyEndFacility, replanDepartureTimeInSeconds,
				dummyStartActivity, destinationActivity);
		
		
		// CHOOSE NEW ROUTE/MODE //
		
		TripCandidate chosenTrip = SelectTripCandidate(availableTripCandidates);

		
		// INSERT NEW ROUTE INTO CURRENT LEG //
		
		// prepare new route elements for insertion: includes some checks
		// splice it into the current route!
		
		//create our container for our new elements.....
		List<? extends PlanElement> insertElements;
		//or...
		//List<Id<Link>> newLinkIds = new ArrayList<Id<Link>>();
		
		
		// check to see if we actually got a routed trip....
		//TODO do we need to break it down into legs and use EditRoutes.spliceNewPathIntoOldRoute instead?
		if (chosenTrip instanceof RoutedTripCandidate) {
			RoutedTripCandidate routedCandidate = (RoutedTripCandidate) chosenTrip;
			insertElements = routedCandidate.getRoutedPlanElements();
			log.info("looks like we got a routed trip candidate for agent " + agentID + ", as expected");
		} else {
			log.info("finalTripCandidate for agent " + agentID + " is not a RoutedTripCandidate...this is wierd, pleae debug IvtCurrentLegReplannerChooseMode");
			return false;
		}
		
		//now insert that new "trip" into the executed plan. NOTE: not sure this will work, since we have a dummy starting activity for our "trip". 
		/*HAHA, it does not work. TripRouter.insertTrip throws a RuntimeException. 
		 * 2021-11-24T15:46:58,655 ERROR ParallelReplanner$ExceptionHandler:341 Thread ParallelDuringLegReplanner2 died with exception while replanning.
java.lang.RuntimeException: could not find origin act [type=WDRReplanning][coord=[x=1209874.092504125 | y=6148177.823321419]][linkId=15389][startTime=undefined][endTime=undefined][duration=undefined][facilityId=null] in [act [type=home][coord=[x=1209881.3 | y=6148182.4]][linkId=15389][startTime=undefined][endTime=06:01:02][duration=undefined][facilityId=home_72232], leg [mode=car][depTime=06:01:02][travTime=00:03:54][arrTime=06:04:56][route= startLinkId=15389 endLinkId=10570 travTime=OptionalTime[234.0] dist=2640.456803705902 linkIds=[15390, 15388, 15402, 8187, 8185, 8183, 8181, 15340, 15332, 36116, 36118, 36114, 37776, 37774, 50820, 50822, 10510, 10506] travelCost=NaN], act [type=other][coord=[x=1210429.79 | y=6147819.17]][linkId=10570][startTime=06:09:02][endTime=06:21:02][duration=undefined][facilityId=sec_19847], leg [mode=car][depTime=06:21:02][travTime=00:08:44][arrTime=06:29:46][route= startLinkId=10570 endLinkId=789 travTime=OptionalTime[524.0] dist=9201.537991469706 linkIds=[10571, 10507, 10511, 50823, 50821, 37775, 37777, 36115, 36119, 36117, 15333, 15341, 15339, 15337, 15335, 50819, 35521, 35519, 35517, 35515, 35513, 35523, 31899, 31897, 31895, 31893, 31891, 31889, 31887, 31885, 11203, 11204, 12256, 12298, 12296, 51145, 51146, 51076, 5917, 5892, 19452, 56152, 56154, 787, 788, 40265, 19445, 795] travelCost=NaN], act [type=work][coord=[x=1207663.15 | y=6153795.78]][linkId=789][startTime=06:31:02][endTime=15:06:02][duration=undefined][facilityId=work_2223], leg [mode=car][depTime=15:06:02][travTime=00:08:29][arrTime=15:14:31][route= startLinkId=789 endLinkId=15389 travTime=OptionalTime[509.0] dist=8248.731352619663 linkIds=[60312, 40264, 40266, 29069, 19438, 56157, 56155, 56153, 19453, 5893, 5918, 25803, 51148, 51149, 51150, 51143, 26723, 12297, 12255, 11206, 11200, 11201, 11202, 31884, 31886, 31888, 31890, 31892, 31894, 31896, 31898, 35522, 35512, 35514, 35516, 35518, 35520, 50818, 15334, 15336, 15338, 8180, 8182, 8179, 8186, 15401, 15387] travelCost=NaN], act [type=home][coord=[x=1209881.3 | y=6148182.4]][linkId=15389][startTime=15:16:02][endTime=18:31:02][duration=undefined][facilityId=home_72232], leg [mode=car][depTime=18:31:02][travTime=00:05:49][arrTime=18:36:51][route= startLinkId=15389 endLinkId=61934 travTime=OptionalTime[349.0] dist=2715.0508082998704 linkIds=[15390, 15388, 15402, 8187, 8185, 8183, 8181, 15340, 15332, 36116, 52470, 36096, 36098, 37583, 37581, 52550, 61937, 61935] travelCost=NaN], act [type=other][coord=[x=1209087.618932 | y=6147250.972354]][linkId=61934][startTime=18:39:02][endTime=18:50:37][duration=undefined][facilityId=sec_21002], leg [mode=car][depTime=18:50:37][travTime=00:05:02][arrTime=18:55:39][route= startLinkId=61934 endLinkId=15389 travTime=OptionalTime[302.0] dist=2324.3677210864385 linkIds=[61936, 52549, 37580, 37582, 36097, 36095, 52469, 36117, 15333, 15341, 8180, 8182, 8179, 8186, 15401, 15387] travelCost=NaN], act [type=home][coord=[x=1209881.3 | y=6148182.4]][linkId=15389][startTime=19:00:37][endTime=20:25:37][duration=undefined][facilityId=home_72232], leg [mode=car][depTime=20:25:37][travTime=00:01:51][arrTime=20:27:28][route= startLinkId=15389 endLinkId=8175 travTime=OptionalTime[111.0] dist=706.1388663539846 linkIds=[15391, 8241, 36505, 61408, 8178, 8176] travelCost=NaN], act [type=leisure][coord=[x=1210066.2266 | y=6148332.4147]][linkId=8175][startTime=20:40:37][endTime=23:25:37][duration=undefined][facilityId=sec_15826], leg [mode=car][depTime=23:25:37][travTime=00:01:00][arrTime=23:26:37][route= startLinkId=8175 endLinkId=15389 travTime=OptionalTime[60.0] dist=289.70462415986657 linkIds=[8177, 61407, 36504, 5173] travelCost=NaN], act [type=home][coord=[x=1209881.3 | y=6148182.4]][linkId=15389][startTime=23:29:37][endTime=undefined][duration=undefined][facilityId=home_72232]]
	at org.matsim.core.router.TripRouter.insertTrip(TripRouter.java:290) ~[matsim-13.0.jar:?]
	at org.matsim.core.router.TripRouter.insertTrip(TripRouter.java:245) ~[matsim-13.0.jar:?]
	at ivtReplanners.IvtCurrentLegReplannerChooseMode.doReplanning(IvtCurrentLegReplannerChooseMode.java:290) ~[classes/:?]
	at org.matsim.withinday.replanning.parallel.ReplanningRunnable.doReplanning(ReplanningRunnable.java:170) ~[matsim-13.0.jar:?]
	at org.matsim.withinday.replanning.parallel.ReplanningRunnable.run(ReplanningRunnable.java:220) ~[matsim-13.0.jar:?]
	at java.lang.Thread.run(Thread.java:832) [?:?]
		*/
		//DEBUG: the problem is that TripRouter.insertTrip looks for dummyStartActivity inside the excecuted Plan.
		// Obviously, it does not exist inside the excecuted plan since we created it to make the mode choice stuff work. 
		// TRY 1: use EditRoutes.spliceNewPathIntoOldRoute
		// TRY 2: write my own insertTrip that can deal with links as insertion points
		//TripRouter.insertTrip(executedPlan, dummyStartActivity, insertElements, destinationActivity);
		
		//TODO use EditRoutes.spliceNewPathIntoOldRoute. Probably need to instantiate an instance of EditRoutes. etc.
		
		//extract the new route from the insertElements...or from the routed and chosen DMCtrip object?
		
		int numberOfElementsInInsertElements = 0;
		
		for (PlanElement pe : insertElements) {
			if (!(pe instanceof Leg)) {
				log.warn("insertElements for agent " + agentID + " contains a plan element that is not a LegImpl. Interesting. Think about debugging.");
			}
			
			numberOfElementsInInsertElements += 1;
			log.info("insertElements for agent " + agentID + " contains this many elements that are Legs: " + numberOfElementsInInsertElements + "If this is more than 1, only the first element will be used. Think about debugging.");

		}
		
		Leg legToInsert = (Leg) insertElements.get(0);
		
		Route routeToInsert = legToInsert.getRoute();
		
		List<Id<Link>> linkIdsToInsert = getRouteLinkIds(routeToInsert, agentID);
		
		spliceNewPathIntoOldRoute(currentLinkIndex, toLinkId, oldRoute, linkIdsToInsert, currentLinkId);
		
		/*log.info("We managed to run EditRoutes.spliceNewPathIntoOldRoute inside IvtCurentLegReplannerChooseMode, "
				+ "but CAUTION: this may have done wierd stuff, check the executed plans to make sure it actually"
				+ " spliced the new route into the current leg correctly!");
		*/
		log.info("We managed to run spliceNewPathIntoOldRoute while replanning agent " + agentID +". This is a beta version. A test to check that it was spliced correctly should be written.");
		
		// Finally reset the cached Values of the PersonAgent - they may have changed!
		WithinDayAgentUtils.resetCaches(withinDayAgent); //TODO is this necessary in this new context? It's from the default WDR CurrentLegReplanner
		
		log.info("We managed to get to the last line of doReplanning inside IvtCurrentLegReplannerChooseMode for the agent with id " + agentID + " & reset the agent's cash, the later of which might not be necessary?");
		return true;
	}
	
	private static List<Id<Link>> getRouteLinkIds(Route route, Id<Person> agentID) {
		List<Id<Link>> linkIds = new ArrayList<>();

		if (route instanceof NetworkRoute) {
			NetworkRoute networkRoute = (NetworkRoute) route;
			linkIds.add(networkRoute.getStartLinkId());
			linkIds.addAll(networkRoute.getLinkIds());
			linkIds.add(networkRoute.getEndLinkId());
		} else {
			log.info("Route of agent " + agentID + " was not a NetworkRoute. this could cause problems");
			//throw new RuntimeException("Currently only NetworkRoutes are supported for Within-Day Replanning! Error thrown while replanning agent " + agentID + ".");
		}

		return linkIds;
	}
	
	private ArrayList<String> DetermineAvailableModes (Person agent, Boolean hasLicense, String currentMode){
	//Create list of modes available to agent
			ArrayList<String> modesAvailable = new ArrayList<String>();
			
			//Agent can only use a car if it has a lisence.
			//Also, if agent is currently driving a car
			//It is unlikely that they will stop driving it and abandon it.
			//Thus the agent who is driving will continue to drive. 
			//TODO are there reasonable exceptions? Like might an agent find the nearest Park & Ride pt station? 
			if (hasLicense && currentMode.equals("car")) {
				modesAvailable.add("car");
			}
			
			//If agent is currently riding their bike, then they continue to do so.
			//Because likely it is their own bike and they won't just abandon it. 
			//TODO improve this logic later with trip chain considerations
			if (currentMode.equals("bike")) {
				modesAvailable.add("bike");
			}
			
			//In all other cases, I'm going to assume they are walking to using pt.
			//So they can use the non-chain dependent modes. Like pt and walk. 
			//TODO later, given scenario can simulate these modes, add taxi, bike sharing, shuttles, etc.
			//TODO NOTE THAT THIS CHANGES THE MODE OF CAR PASSENGERS. Should improve logic for car passengers!
			if (!currentMode.equals("car") & !currentMode.equals("bike")) {
				modesAvailable.add("pt");
				modesAvailable.add("walk");
			}
			
			return modesAvailable;
	}
	
	private ArrayList<TripCandidate> CreateTripCandidates (Person agent, ArrayList<String> modesAvailable, 
			Plan executedPlan, Facility fromFacility, Facility toFacility, double departureTimeInSeconds,
			Activity currentActivity, Activity destinationActivity) {	
		
		ArrayList<TripCandidate> newlyRoutedCandidateList = new ArrayList<TripCandidate>();
		
		for (String mode : modesAvailable) {
			
			if (mode.equals("car")){
				//ROUTE TRIP
				List<? extends PlanElement> newlyRoutedTripElements = tripRouter.calcRoute(mode, fromFacility, toFacility, departureTimeInSeconds, agent);
	
				//need to create a DMC TRIP out of trip elements....and the original trip we are replanning
				DiscreteModeChoiceTrip newlyRoutedDMCTrip = new DiscreteModeChoiceTrip(currentActivity, destinationActivity, mode, newlyRoutedTripElements, executedPlan.getPerson().hashCode(), 0, 0);

				//Define the departure time, since when you create a DiscreteModeChoiceTrip, the departure time remains undefined. 
				OptionalTime newDepartureTime = ((Leg) newlyRoutedTripElements.get(0)).getDepartureTime(); //the TripElements have the departure time stored, so retrieve it from them
				newlyRoutedDMCTrip.setDepartureTime(newDepartureTime.seconds()); //now give the departure time to the DMCTrip. It requires it to be formatted as seconds. 
				
				//Now,find the duration of the trip...this is my attempt to replicate how it is done in eqasim. Not sure if I should copy that, but for now I did. 
				//TODO should I use this method for finding the duration? Or just retrieve that from the tripElements, which store "travTime" for legs?
				TimeInterpreter time = timeInterpreterFactory.createTimeInterpreter(); //Why inject a factory instead of the TimeInterpreter??? //TODO does the factory need to be final? why? //TODO is the correct TimeInterpreter impl. being created?
				time.setTime(newlyRoutedDMCTrip.getDepartureTime());
				time.addPlanElements(newlyRoutedTripElements);
				double duration = time.getCurrentTime() - newlyRoutedDMCTrip.getDepartureTime();
				
				//calculate Utility (backup, in case does get thrown in MNL, or logic changes)
				double carUtility = utilityEstimatorsMap.get(EqasimModeChoiceModule.CAR_ESTIMATOR_NAME).estimateUtility(agent, newlyRoutedDMCTrip, newlyRoutedTripElements);
				
				DefaultRoutedTripCandidate newlyRoutedTripCandidate = new DefaultRoutedTripCandidate(carUtility, mode, newlyRoutedTripElements, duration);

				newlyRoutedCandidateList.add(newlyRoutedTripCandidate);
				break;
				
				//car is not added to the selector because it is automatically selected!
				//TODO incorporate chain-dependent mode constraints more elegantly
				
			} else if (mode.equals("bike")) {
				//ROUTE TRIP
				List<? extends PlanElement> newlyRoutedTripElements = tripRouter.calcRoute(mode, fromFacility, toFacility, departureTimeInSeconds, agent); 

				//need to create a DMC TRIP out of trip elements....and the original trip we are replanning
				DiscreteModeChoiceTrip newlyRoutedDMCTrip = new DiscreteModeChoiceTrip(currentActivity, destinationActivity, mode, newlyRoutedTripElements, executedPlan.getPerson().hashCode(), 0, 0);

				//Define the departure time, since when you create a DiscreteModeChoiceTrip, the departure time remains undefined. 
				OptionalTime newDepartureTime = ((Leg) newlyRoutedTripElements.get(0)).getDepartureTime(); //the TripElements have the departure time stored, so retrieve it from them
				newlyRoutedDMCTrip.setDepartureTime(newDepartureTime.seconds()); //now give the departure time to the DMCTrip. It requires it to be formatted as seconds. 
				
				//Now,find the duration of the trip...this is my attempt to replicate how it is done in eqasim. Not sure if I should copy that, but for now I did. 
				//TODO should I use this method for finding the duration? Or just retrieve that from the tripElements, which store "travTime" for legs?
				TimeInterpreter time = timeInterpreterFactory.createTimeInterpreter(); //Why inject a factory instead of the TimeInterpreter??? //TODO does the factory need to be final? why? //TODO is the correct TimeInterpreter impl. being created?
				time.setTime(newlyRoutedDMCTrip.getDepartureTime());
				time.addPlanElements(newlyRoutedTripElements);
				double duration = time.getCurrentTime() - newlyRoutedDMCTrip.getDepartureTime();
				
				//calculate Utility (backup, in case does get thrown in MNL, or logic changes)
				double bikeUtility = utilityEstimatorsMap.get(EqasimModeChoiceModule.BIKE_ESTIMATOR_NAME).estimateUtility(agent, newlyRoutedDMCTrip, newlyRoutedTripElements);
				
				DefaultRoutedTripCandidate newlyRoutedTripCandidate = new DefaultRoutedTripCandidate(bikeUtility, mode, newlyRoutedTripElements, duration);

				newlyRoutedCandidateList.add(newlyRoutedTripCandidate);
				break;
				//bike is not added to the selector because it is automatically selected!
				//TODO incorporate chain-dependent mode constraints more elegantly
				
			} else if (mode.equals("pt") || mode.equals("walk")) {
				
				
				
				if (mode.equals("pt")) {
					// ROUTE TRIP
					List<? extends PlanElement> newlyRoutedTripElements = tripRouter.calcRoute(mode, fromFacility, toFacility, departureTimeInSeconds, agent);
					
					DiscreteModeChoiceTrip newlyRoutedDMCTrip = new DiscreteModeChoiceTrip(currentActivity, destinationActivity, mode, newlyRoutedTripElements, executedPlan.getPerson().hashCode(), 0, 0);

					//Define the departure time, since when you create a DiscreteModeChoiceTrip, the departure time remains undefined. 
					OptionalTime newDepartureTime = ((Leg) newlyRoutedTripElements.get(0)).getDepartureTime(); //the TripElements have the departure time stored, so retrieve it from them
					newlyRoutedDMCTrip.setDepartureTime(newDepartureTime.seconds()); //now give the departure time to the DMCTrip. It requires it to be formatted as seconds. 
					
					//Now,find the duration of the trip...this is my attempt to replicate how it is done in eqasim. Not sure if I should copy that, but for now I did. 
					//TODO should I use this method for finding the duration? Or just retrieve that from the tripElements, which store "travTime" for legs?
					TimeInterpreter time = timeInterpreterFactory.createTimeInterpreter(); //TODO does the factory need to be final? why? //TODO is the correct TimeInterpreter impl. being created?
					time.setTime(newlyRoutedDMCTrip.getDepartureTime());
					time.addPlanElements(newlyRoutedTripElements);
					double duration = time.getCurrentTime() - newlyRoutedDMCTrip.getDepartureTime();
					
					//Estimate Utility
					double ptUtility = utilityEstimatorsMap.get(EqasimModeChoiceModule.PT_ESTIMATOR_NAME).estimateUtility(agent, newlyRoutedDMCTrip, newlyRoutedTripElements);
					
					//make DefaultRoutedTripCandidate 
					DefaultRoutedTripCandidate newlyRoutedTripCandidate = new DefaultRoutedTripCandidate(ptUtility, mode, newlyRoutedTripElements, duration);
					
					newlyRoutedCandidateList.add(newlyRoutedTripCandidate);

				
				} else if (mode.equals("walk")) {
					List<? extends PlanElement> newlyRoutedTripElements = tripRouter.calcRoute(mode, fromFacility, toFacility, departureTimeInSeconds, agent);
					
					DiscreteModeChoiceTrip newlyRoutedDMCTrip = new DiscreteModeChoiceTrip(currentActivity, destinationActivity, mode, newlyRoutedTripElements, executedPlan.getPerson().hashCode(), 0, 0);

					//Define the departure time, since when you create a DiscreteModeChoiceTrip, the departure time remains undefined. 
					OptionalTime newDepartureTime = ((Leg) newlyRoutedTripElements.get(0)).getDepartureTime(); //the TripElements have the departure time stored, so retrieve it from them
					newlyRoutedDMCTrip.setDepartureTime(newDepartureTime.seconds()); //now give the departure time to the DMCTrip. It requires it to be formatted as seconds. 
					
					//Now,find the duration of the trip...this is my attempt to replicate how it is done in eqasim. Not sure if I should copy that, but for now I did. 
					//TODO should I use this method for finding the duration? Or just retrieve that from the tripElements, which store "travTime" for legs?
					TimeInterpreter time = timeInterpreterFactory.createTimeInterpreter(); //TODO does the factory need to be final? why? //TODO is the correct TimeInterpreter impl. being created?
					time.setTime(newlyRoutedDMCTrip.getDepartureTime());
					time.addPlanElements(newlyRoutedTripElements);
					double duration = time.getCurrentTime() - newlyRoutedDMCTrip.getDepartureTime();
					
					//Estimate Utility
					double walkUtility = utilityEstimatorsMap.get(EqasimModeChoiceModule.WALK_ESTIMATOR_NAME).estimateUtility(agent, newlyRoutedDMCTrip, newlyRoutedTripElements);
					
					//make DefaultRoutedTripCandidate 
					DefaultRoutedTripCandidate newlyRoutedTripCandidate = new DefaultRoutedTripCandidate(walkUtility, mode, newlyRoutedTripElements, duration);
					
					newlyRoutedCandidateList.add(newlyRoutedTripCandidate);
					
				}
			}
		}
		return newlyRoutedCandidateList;
	}
	
	private TripCandidate SelectTripCandidate (ArrayList<TripCandidate> tripCandidateList) {
		//create a container for the final trip candidate
		TripCandidate selectedTripCandidate = null;
		
		//create the "selector" aka choice model
		MultinomialLogitSelector selector = new MultinomialLogitSelector(10000.00, -10000, false);
		
		//provide random seed for choice model (choice model is probabilistic)
		//TODO is this the best way to generate a random number? Should I try to access the same 
		//number as passed to the DiscreteModeChoiceAlgorithm in the normal eqasim replanning phase
		//between iterations? See DiscreteModeChoiceReplanningModule line 41. 
		Random random = MatsimRandom.getLocalInstance(); 
		
		//add to choice set
		if (tripCandidateList.size() == 1) { //if list size is 1, then only one candidate exists. No need for a choice model. 
			
			selectedTripCandidate = tripCandidateList.get(0);
			
			return selectedTripCandidate;

			} else if(tripCandidateList.size() != 0 && tripCandidateList != null){ //we have multiple candidates. Now we need to choose. 
				
				//add trip candidates to the selector
				for (TripCandidate tripCandidate : tripCandidateList){
					
					selector.addCandidate(tripCandidate);
				}
				
				//choose trip candidate, aka run the selector
				Optional<UtilityCandidate> selectedCandidate = selector.select(random);
				
				//Assign trip as the one selected by selector
				selectedTripCandidate = (TripCandidate) selectedCandidate.get();
				return selectedTripCandidate;
				
			} else { // we have no candidates, something went wrong! 
				//TODO Throw error instead
				log.info("tripCandidateList is equal to null. This is wierd. Please debug IvtNextLegReplannerChooseMode");
				return null;
			}
	}
	
	private static void spliceNewPathIntoOldRoute(int currentLinkIndex, Id<Link> toLinkId, NetworkRoute oldRoute,

			  List<Id<Link>> newLinksIds, Id<Link> currentLinkId) {
List<Id<Link>> oldLinkIds = oldRoute.getLinkIds();;
List<Id<Link>> resultingLinkIds = new ArrayList<>();

// REMEMBER: the "currentLinkIndex" does not point to the current position, but one beyond. Probably
// because there is a starting link, which is not part of getLinkIds().

/*
* Get those Links which have already been passed.
* oldLinkIds contains also the startLinkId, which should not
* be part of the List - it is set separately. Therefore we start
* at index 1.  CD, 201X
* --> I cannot confirm this from the code.  Maybe it was like that in earlier times?  kai, feb'18
*/
if (currentLinkIndex > 0) {
//log.warn("oldLinkIds.size = " + oldLinkIds.size() + "; currentLinkIndex = " + currentLinkIndex ) ;
//resultingLinkIds.addAll(oldLinkIds.subList(1, currentLinkIndex + 1));
resultingLinkIds.addAll(oldLinkIds.subList(0, currentLinkIndex-1));
// (1) I cannot confirm the "starting at 1" from the code, as stated above.  Maybe it was like that in earlier code.
// (2) [0,current-1[ now means [0,current-2].  Since "current-1" is the current link, current-2 is one before.
// This will then be compensated below.
}
if ( !oldRoute.getStartLinkId().equals( currentLinkId ) ) {
// (this happens if the agent is still on the departure link: that is not part of getLinkIds().  Otherwise:   )
resultingLinkIds.add(currentLinkId);
}

// Merge old and new Route.
/*
* Edit cdobler 25.5.2010
* If the new leg ends at the current Link, we have to
* remove that linkId from the linkIds List - it is stored
* in the endLinkId field of the route.
* --> This is essentially the addition of the "currentLinkId" above, which
* could now just be suppressed in this situation.  kai, feb'18
* --> I just tried that, but at least the obvious approach does not pass the tests. kai, feb'18
*/
//if (newLinkIds.size() > 0 && path.links.size()>0 && newLinkIds.get(newLinkIds.size() - 1).equals( path.links.get( path.links.size()-1 ) ) ) {
if (resultingLinkIds.size() > 0 && newLinksIds.size()>0
&& resultingLinkIds.get(resultingLinkIds.size() - 1).equals( newLinksIds.get( newLinksIds.size()-1 ) )
) {
resultingLinkIds.remove( resultingLinkIds.size()-1 );
}

resultingLinkIds.addAll( newLinksIds ) ;

StringBuilder strb = new StringBuilder() ;
for ( int ii= Math.max(0,currentLinkIndex-2) ; ii < Math.min( resultingLinkIds.size(), currentLinkIndex+3) ; ii++ ) {
strb.append("-") ;
strb.append( resultingLinkIds.get(ii) ) ;
strb.append("-") ;
}
log.info( "linkIds at join: " + strb ) ;

// Overwrite old Route
oldRoute.setLinkIds(oldRoute.getStartLinkId(), resultingLinkIds, toLinkId );
}
	
	private static List<PlanElement> spliceReplannedLegIntoOldPlan (int currentLinkIndex, List<PlanElement> currentPlanElements,
			List<PlanElement> wdreplannedPlanElements, Leg currentLeg, MobsimAgent agent){
		
		/*
		 * The idea:
		 * 1) Find the index of the Leg being replanned inside the PlanElements list
		 * 2) Split it into two Legs, namely 1) the already executed Leg and 2) the not-yet-executed Leg
		 * 3) Extract the Replanned Leg from the wdrreplannedPlanElements
		 * 4) Retrieve the "dummyStartActivity" aka the wdr interaction Activity
		 * 5) Insert the 1) already executed Leg 2) wdr interaction Activity 3) Replanned Leg into the PlanElements list, aka the plan
		 * 6) Return the modified PlanElements list....to do this, use what i call the "subList" trick (as done in TripRouter.insertTrip)
		 */
		
		// 1) Find the index of the Leg being replanned. 
		// We have the PlanElement being replanned, we extract that from the WithinDayAgent at the beginning of doReplanning, and we've passed it to this method as an argument
		int currentLegIndex = WithinDayAgentUtils.indexOfPlanElement(agent, currentLeg);
		
		// 2) Split it into two Legs, namely 1) the already executed Leg and 2) the not-yet-executed Leg
		// So we need to retrieve the already executed parts of our currentLeg
		// We have the currentLinkIndex passed as an argument. 
		// Now it gets tricky. Depending on the mode, we have different route types. Not all of these have links. 
		//	- for network routes that have links (route type ="links") one can look for the link index and split the route
		// 	- for default pt routes (route type ="default_pt") there are only start and end links, and pt-specific route attributes.
		// 	- for generic routes (route type ="generic"), used for non-pt, non-network modes like walk and bike, there is only start and end links and travel time (aka the agent is "teleported")
		
		
		
		List<PlanElement> seq = currentPlanElements.subList(1, 2);
		List<PlanElement> oldTrip = new ArrayList<>( seq );
		seq.clear();
		assert wdreplannedPlanElements != null;
		seq.addAll(wdreplannedPlanElements);
		
		return oldTrip; 
		
		
	}
}
