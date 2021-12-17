
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

package ivtReplanners;

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
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
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
import org.matsim.core.population.PersonUtils;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.Facility;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringActivityReplanner;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayReplanner;
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
public class IvtNextLegReplannerChooseModeTest extends WithinDayDuringActivityReplanner {

	private static final Logger log = Logger.getLogger(EditTrips.class) ;
	
	private final TripRouter tripRouter;
	
	//here we are retrieving the map of utility estimators created (I think?) when the IDFModeChoiceModule is ...initialized?
	//This is important, so that we can later just access the utility estimators for each mode
	//TODO Question: why does the @Inject at the field not work? The field remains null, but the replanner code runs without errors up until it needs a utility estimator. 
	private Map<String, UtilityEstimator> utilityEstimatorsMap;  
	//@Inject private PtUtilityEstimator ptUtilityEstimator;
	private WalkUtilityEstimator walkUtilityEstimator;
	//does the time interpreter factory need to be final???? Because then I need to pass it to the constructor and I don't understand how that's done in eqasim...HELP
	private final TimeInterpreter.Factory timeInterpreterFactory;

	private IDFCarUtilityEstimator carUtilityEstimator;

	private IDFBikeUtilityEstimator bikeUtilityEstimator;

	//TODO provide selector factory and time interpreter factory via replanner factory -....do I need to do this via "provider"????
	IvtNextLegReplannerChooseModeTest(Id<WithinDayReplanner> id, Scenario scenario, ActivityEndRescheduler internalInterface, TripRouter tripRouter,
			TimeInterpreter.Factory timeInterpreterFactory, Map<String, UtilityEstimator> utilityEstimatorsMap) {
		super(id, scenario, internalInterface);
		this.tripRouter = tripRouter;
		this.timeInterpreterFactory = timeInterpreterFactory;
		this.utilityEstimatorsMap = utilityEstimatorsMap;
	}

	@Override
	public boolean doReplanning(MobsimAgent withinDayAgent) {
		
		log.info("We entered doReplanning inside NextLegReplannerChooseMode");
		
		Plan executedPlan = WithinDayAgentUtils.getModifiablePlan(withinDayAgent);

		// If we don't have an executed plan
		if (executedPlan == null) return false;

		// If there is no trip after the activity.
		if (executedPlan == null) return false;
		
		/*In this replanner, the agent will perform mode choice using the results of an MNL. 
		 * For this, we need to route all the choices the agent has available.
		 * We also will need the planned departure time, especially for pt routing.
		 *  Thus, we need to: */
		
		
		// Get the activity currently performed by the agent as well as the subsequent trip.
		Activity currentActivity = (Activity) WithinDayAgentUtils.getCurrentPlanElement(withinDayAgent);
		Trip trip = TripStructureUtils.findTripStartingAtActivity( currentActivity, executedPlan );
		Activity destinationActivity = trip.getDestinationActivity();
		
		//To extract the current location 
		Facility fromFacility = FacilitiesUtils.toFacility(trip.getOriginActivity(), scenario.getActivityFacilities());
		//To extract the destination of the trip
		Facility toFacility = FacilitiesUtils.toFacility(trip.getDestinationActivity(), scenario.getActivityFacilities());
		//To extract the planned departure time
		OptionalTime departureTime = TripStructureUtils.getDepartureTime(trip);
		double departureTimeInSeconds = departureTime.seconds();
		
		/*It would also be useful to know which mode the agent had planned to use. There are studies showing that in disruptive situations 
		many travelers demonstrate a sort of "inertia" with regards to switching modes, even if they theoretically could do so. */
		
		//TODO To extract the planned mode of the upcoming trip
		//first note that the variable trip is not a list of plan elements, oddly enough. 
		//So we need to get the trip as a list of plan elements.
		List<PlanElement> tripElements = trip.getTripElements();
		//Then we extract the "main mode". So like, not the access walk mode, but the pt mode of the main leg...
		String plannedMode = TripStructureUtils.identifyMainMode(tripElements);
		
		/*Next, we need to know more about the agent. 
		 *Which modes can the agent access? At minimum, we need to know if they have a driver's liscence or a transit pass.
		 *Ideally, we could also use details such as their gender, age, income, trip purpose, airline ticket class, if they have checked luggage, etc.
		 *But first, let us just focus on mobility tool availability */
		
		//First, retrieve the actual Person object for the current agent being replanned...
		//Still wish there was just a method to get the person with the id...not this two step stuff - cvl Sept. 2021
		Id<Person> agentID = withinDayAgent.getId();
		Person agent = scenario.getPopulation().getPersons().get(agentID);
		
		
		//All the above can be consolidated by just converting the executedPlan into a DiscreteModeChoiceTrip
		//The advantage of this is that now the eqasim utilities can be used (utility estimators, cost estimators, etc.)
		//TODO test if this conversion has side effects, since it isn't designed for a WDR application and we are feeding it an executed plan!!
		DiscreteModeChoiceTrip dmcTrip = new DiscreteModeChoiceTrip(currentActivity, destinationActivity, plannedMode, tripElements, executedPlan.getPerson().hashCode(), 0, 0);
		
		//then, use PersonUtils to extract the desired agent attributes
		Boolean hasLicense = PersonUtils.hasLicense(agent);
		log.info("Agent " + agentID + "has a drivers license: " + hasLicense);
		
		//using the getAttributes() method. I assume that using equas() is correct.
		//TODO test if it actually calls on the right attribute. 
		Boolean hasPTSubscription = agent.getAttributes().equals("hasPtSubscription");
		log.info("Agent " + agentID + "has a pt subscription: " + hasPTSubscription);
		
		
		//**** CHOOSING LOGIC ****
		
		
		ArrayList<String> modesAvailable = DetermineAvailableModes(agent, hasLicense, plannedMode);
		log.info("Available modes are:" + modesAvailable);
		
		ArrayList<TripCandidate> availableTripCandidates = CreateTripCandidates (agent, modesAvailable,
				executedPlan, fromFacility, toFacility, departureTimeInSeconds,
				currentActivity, destinationActivity,
				tripElements, dmcTrip);
		
		TripCandidate chosenTrip = SelectTripCandidate(availableTripCandidates);
		log.info("chosen trip has mode: " + chosenTrip.getMode() + " and utility of: " + chosenTrip.getUtility() + " and duration of: " + chosenTrip.getDuration());
		
		List<? extends PlanElement> insertElements;

		if (chosenTrip instanceof RoutedTripCandidate) {
			RoutedTripCandidate routedCandidate = (RoutedTripCandidate) chosenTrip;
			insertElements = routedCandidate.getRoutedPlanElements();
			log.info("looks like we got a routed trip candidate, as expected");
		} else {
			log.info("finalTripCandidate was not routed...this is wierd, pleae debug IvtNextLegReplannerChooseMode");
			log.info("So for now, just inserted the old trip!");
			insertElements = tripElements;
		}


		//Then that re-routed leg needs to be spliced into the agent's plan - here use the available methods from EditTrips (or Leg...or Plan...will need to check!)
		TripRouter.insertTrip(executedPlan, trip.getOriginActivity(), insertElements, trip.getDestinationActivity());
		
		//Then make sure that the new plan is stored in the right place so that the agent actually uses it in the following timesteps!! (check EditTrips, etc., for this method!)
		//TODO find or create tests to make sure revised trip is
		// a) being spliced into the plan correctly
		// b) being executed correctly

		// TODO Double Check: WDR warnings say that to replan pt legs, we would need internalInterface of type InternalInterface.class...is this true?
		log.info("We managed to get to the last line of doReplanning inside IvtNextLegReplannerChooseMode");
				
		
		return true;
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
				log.info("tripCandidateList is equal to null. This is wierd. Please debug IvtNextLegReplannerChooseModeTest");
				return null;
			}
	}
	
	private ArrayList<String> DetermineAvailableModes (Person agent, Boolean hasLicense, String plannedMode){
	//Create list of modes available to agent
		//This is a test replanner, I want to see if the choice model does something! 
		//So I am giving each agent that get's replanned access to all four modes. 
			ArrayList<String> modesAvailable = new ArrayList<String>();
			
			//TODO need to get eqasim to just give people cars! gosh. 
			//In the meantime, only give agents cars that have licenses and that planned to use their cars
			/*if (hasLicense && plannedMode.equals("car")) {
				modesAvailable.add("car");
			}*/
			modesAvailable.add("bike");
			modesAvailable.add("pt");
			modesAvailable.add("walk");
			
			return modesAvailable;
	}
	
	private ArrayList<TripCandidate> CreateTripCandidates (Person agent, ArrayList<String> modesAvailable, 
			Plan executedPlan, Facility fromFacility, Facility toFacility, double departureTimeInSeconds,
			Activity currentActivity, Activity destinationActivity,
			List<PlanElement> tripElements, DiscreteModeChoiceTrip dmcTrip) {	
		
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
				//break;
				//this is a test replanner, we add all modes to the choice set, regardless. 

				
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
				//break;
				//This is a test replanner, we add all modes to the choice set, regardless. 
				
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
	
}


//Notes and thoughts
//CUSTOM ROUTING (currently not the chosen way of doing this)
//route alternatives and place them in the container for routed alternatives
/*for (String mode : modesAvailable) {
	final List<? extends PlanElement> TripAlternative = tripRouter.calcRoute(mode, fromFacility, toFacility, depatureTimeInSeconds, agent);
	alternativeTrips.add(TripAlternative);
}*/

//CUSTOM UTILITY CALCULATION (currently not the chosen way of doing this)
 /*Then, we extract the relevant route attributes for the mode choice model. 
 * For all modes, this includes the access & egress time and access & egress distance. 
 * For car and other individual modes, the other attributes would be simply travel time 
 * and distance(to allow cost calculation). Perhaps tolls.
 * For pt, this needs to including waiting time and number of transfers, 
 * and possibly also transfer times/transfer waiting times. */

//Extract route attributes
//Access and egress time and distance
//travel time
//travel distance
//waiting time
//number of transfers
//transfer waiting times?

/*Once we have route attributes, we need to calculate costs!*/

//Calculate distance based cost
//for car
//for pt
//for others?

/*Once we have all route attributes in the form that we need for the MNL,
 * we plug those values into the utility functions and calculate the utilities */

//TODO calculate utilities
//define parameters (later use injection!)
//write out and calculate utilities, store utilities
//FOR NOW, UNUSUABLE MODES WILL SIMPLY HAVE A UTILITY OF ZERO?

//USING EQASIM UTILITIES TO ESTIMATE UTILITIES, ETC.
// Note that we don't know what information the router is using - free flow travel times? Something else?
//TODO make sure the utility estimators are using the desired information!

/*for (String mode : modesAvailable) {
	
	if (mode == "car") {
		//here we use that map that we injected earlier!
		UtilityEstimator carUtilityEstimator = utilityEstimatorsMap.get(IDFModeChoiceModule.CAR_ESTIMATOR_NAME);
		double carUtility = carUtilityEstimator.estimateUtility(agent, dmcTrip, tripElements);
	} else if (mode == "bike") {
		UtilityEstimator bikeUtilityEstimator = utilityEstimatorsMap.get(IDFModeChoiceModule.BIKE_ESTIMATOR_NAME);
		double bikeUtility = bikeUtilityEstimator.estimateUtility(agent, dmcTrip, tripElements);
	}
}*/
/*This is something for later (air travel related):  
 * For consistancy's sake, we need to know which personal (or rented) vehicles they have their current location. 
 * A person who has their private car at their current location is unlikely to just leave it there. 
 * A person with their private bike will be reluctant to leave it there, but perhaps not
 * as reluctant as the car driver-owner. 
 * A person with a car rental will need to get that rental back to the rental agency, and can't switch modes.
 * For now we assume our special airport agents don't have such personal vehicles.
 * But later they might be local agents traveling, or groups, etc.
 * So they might have chain-dependent vehicles that they can't just park and abandon.... 
 */
//TODO (LATER) Extract presence of chain-dependent vehicles (private car, private bicycle, non-free floating rental car)

