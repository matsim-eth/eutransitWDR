
/* *********************************************************************** *
 * project: org.matsim.*
 * CurrentLegReplannerFactory.java
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

import org.eqasim.core.simulation.mode_choice.utilities.UtilityEstimator;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contribs.discrete_mode_choice.model.utilities.UtilitySelectorFactory;
import org.matsim.contribs.discrete_mode_choice.replanning.time_interpreter.TimeInterpreter;
import org.matsim.core.mobsim.qsim.ActivityEndReschedulerProvider;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.withinday.mobsim.WithinDayEngine;
import org.matsim.withinday.replanning.replanners.CurrentLegReplanner;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringActivityReplanner;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringActivityReplannerFactory;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringLegReplanner;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringLegReplannerFactory;

import com.google.inject.Inject;

import java.util.Map;

import javax.inject.Provider;

public class IvtCurrentLegReplannerChooseModeFactory extends WithinDayDuringLegReplannerFactory {

	private final Scenario scenario;
	private final Provider<TripRouter> tripRouterFactory;
	private Map<String, UtilityEstimator> utilityEstimatorMap;
	private TimeInterpreter.Factory timeInterpreterFactory;

	public IvtCurrentLegReplannerChooseModeFactory(Scenario scenario, ActivityEndReschedulerProvider withinDayEngine,
			Provider<TripRouter> tripRouterFactory, TimeInterpreter.Factory timeInterpreterFactory,
			Map<String, UtilityEstimator> utilityEstimatorMap) {
		
		super(withinDayEngine);
		this.scenario = scenario;
		this.tripRouterFactory = tripRouterFactory;
		this.timeInterpreterFactory = timeInterpreterFactory;
		this.utilityEstimatorMap = utilityEstimatorMap;
	}

	@Override
	public WithinDayDuringLegReplanner createReplanner() {
		WithinDayDuringLegReplanner replanner = new IvtCurrentLegReplannerChooseMode(super.getId(), scenario,
				this.getWithinDayEngine().getActivityRescheduler(), 
				this.tripRouterFactory.get(), this.timeInterpreterFactory, this.utilityEstimatorMap, this.scenario.getNetwork());
		return replanner;
	}
	
}
