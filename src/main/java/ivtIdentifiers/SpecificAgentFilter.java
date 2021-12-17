/* project: org.matsim.*
* ProbabilityFilter.java
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

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.qsim.pt.TransitDriverAgent;
import org.matsim.withinday.mobsim.MobsimDataProvider;
import org.matsim.withinday.replanning.identifiers.interfaces.AgentFilter;
import org.matsim.withinday.utils.EditTrips;

public class SpecificAgentFilter implements AgentFilter {

	private static final Logger log = Logger.getLogger(EditTrips.class) ;
	
	private final MobsimDataProvider mobSimDataProvider;
	private final Set<Person> personSet;

	// use the factory
	/* package */ 
	SpecificAgentFilter(Set<Person> personSet, MobsimDataProvider mobsimdataprovider) {
		this.personSet = personSet;
		this.mobSimDataProvider = mobsimdataprovider;
	}

	@Override
	public void applyAgentFilter(Set<Id<Person>> set, double time) {
		Iterator<Id<Person>> iter = set.iterator();

		while (iter.hasNext()) {
			Id<Person> id = iter.next();

			if (!this.applyAgentFilter(id, time))
				iter.remove();
		}
	}

	@Override

	public boolean applyAgentFilter(Id<Person> id, double time) {

		boolean isTrueOrFalse = false;

		Map<Id<Person>, MobsimAgent> mobsimAgentMap = this.mobSimDataProvider.getAgents();

		MobsimAgent agent = mobsimAgentMap.get(id);
		for (Person person : personSet) {

			if (person.getId() == id) {

				if (agent instanceof TransitDriverAgent) {
					isTrueOrFalse = false;
					System.out.println("We found a TransitDriverAgent and returned " + isTrueOrFalse + " . This is strange. Did you accidentally specifiy a TransitDriverAgent?");
					return isTrueOrFalse;
				} else {
					isTrueOrFalse = true;
					System.out.println("We found an agent to replan, namely agent with id " + id + " and returned " + isTrueOrFalse + " .");
					log.info("We found an agent to replan, namely agent with id " + id + " and returned " + isTrueOrFalse + " .");
					return isTrueOrFalse;
				}
			}

		}
		//System.out.println("The agent we just handled is not the agent we are looking for and thus we returned " + isTrueOrFalse + " . You should be returning false. If not, the loop is not working.");
		//log.info("The agent we just handled is not the agent we are looking for and thus we returned " + isTrueOrFalse);
		return isTrueOrFalse;
	}
}
