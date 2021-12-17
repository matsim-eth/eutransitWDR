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
import java.util.Random;
import java.util.Set;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.mobsim.framework.MobsimAgent;
import org.matsim.core.mobsim.qsim.pt.TransitDriverAgent;
import org.matsim.withinday.mobsim.MobsimDataProvider;
import org.matsim.withinday.replanning.identifiers.interfaces.AgentFilter;

public class ProbabilityFilterRemovePtAgents implements AgentFilter {

	private final Random random = MatsimRandom.getLocalInstance();
	private final double replanningProbability;
	private final MobsimDataProvider mobSimDataProvider;

	// use the factory
	/* package */ ProbabilityFilterRemovePtAgents(double replanningProbability, MobsimDataProvider mobsimdataprovider) {
		this.replanningProbability = replanningProbability;
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

		/*
		 * This ensures that the filter's outcomes do not depend on the order in which
		 * agents are filtered. Otherwise agents stored in unsorted data structures will
		 * not produce deterministic outcomes!
		 */
		random.setSeed(id.hashCode() + (long) time);

		/*
		 * Based on a random number it is decided whether an agent should do a
		 * replanning or not. number > replanningProbability: no replanning
		 */
		double rand = random.nextDouble();
		// TODO: filter out transit driver agents
		
		boolean isTrueOrFalse = false;
		
		
		Map<Id<Person>, MobsimAgent> mobsimAgentMap = this.mobSimDataProvider.getAgents();

		if (rand > replanningProbability) {
			isTrueOrFalse = false;
			return isTrueOrFalse;
		} else {
			if (rand <= replanningProbability) {
				//TODO: how to grab the agent object based upon the id object. Apparently, not that easy, because wtf
				MobsimAgent agent = mobsimAgentMap.get(id);
				if (agent instanceof TransitDriverAgent) {
					System.out.println("We found a TransitDriverAgent and returned false.");
					isTrueOrFalse = false;
					return isTrueOrFalse;
				} else {
					System.out.println("We found an agent to replan and returned true.");
					isTrueOrFalse = true;
					return isTrueOrFalse;
				}
			}
		}
		return isTrueOrFalse;
	}


}
