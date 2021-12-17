
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
import org.matsim.core.router.TripRouter;
import org.matsim.withinday.mobsim.WithinDayEngine;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringActivityReplanner;
import org.matsim.withinday.replanning.replanners.interfaces.WithinDayDuringActivityReplannerFactory;

import com.google.inject.Inject;

import java.util.Map;

import javax.inject.Provider;

public class IvtNextLegReplannerFactoryChooseModeTest extends WithinDayDuringActivityReplannerFactory {

	private final Scenario scenario;
	private final Provider<TripRouter> tripRouterFactory;
	private TimeInterpreter.Factory timeInterpretorFactory;
	private Map<String, UtilityEstimator> utilityEstimatorsMap;

	
	public IvtNextLegReplannerFactoryChooseModeTest(Scenario scenario, WithinDayEngine withinDayEngine,
												Provider<TripRouter> tripRouterFactory, TimeInterpreter.Factory timeInterpretorFactory,
												Map<String, UtilityEstimator> utilityEstimatorsMap) {
		super(withinDayEngine);
		this.scenario = scenario;
		this.tripRouterFactory = tripRouterFactory;
		this.timeInterpretorFactory = timeInterpretorFactory;
		this.utilityEstimatorsMap = utilityEstimatorsMap;
	}

	@Override
	public WithinDayDuringActivityReplanner createReplanner() {
		WithinDayDuringActivityReplanner replanner = new IvtNextLegReplannerChooseModeTest(super.getId(), this.scenario,
				this.getWithinDayEngine().getActivityRescheduler(),
				this.tripRouterFactory.get(), this.timeInterpretorFactory,
				this.utilityEstimatorsMap);
		return replanner;
	}

}