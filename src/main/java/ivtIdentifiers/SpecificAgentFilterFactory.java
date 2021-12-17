
/* *********************************************************************** *
 * project: org.matsim.*
 * ProbabilityFilterFactory.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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

package ivtIdentifiers;

import org.matsim.withinday.mobsim.MobsimDataProvider;
import org.matsim.withinday.replanning.identifiers.interfaces.AgentFilterFactory;

import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;

public class SpecificAgentFilterFactory implements AgentFilterFactory {

	private final MobsimDataProvider mobSimDataProvider;
	private final Set<Person> personSet;

	public SpecificAgentFilterFactory(Set<Person> personSet, MobsimDataProvider mobsimdataprovider) {
		this.personSet = personSet;
		this.mobSimDataProvider = mobsimdataprovider;
	}
	
	@Override
	public SpecificAgentFilter createAgentFilter() {
		return new SpecificAgentFilter(this.personSet, this.mobSimDataProvider);
	}
}
