package wdrConfigurators;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.Config;

public class TestWDRModeConfigurator {
	static public void configure(Config config) {
		//config.controler().setRoutingAlgorithmType(RoutingAlgorithmType.FastAStarLandmarks);

		Collection<String> mainModes = new ArrayList<>(config.qsim().getMainModes());
		mainModes.addAll(Arrays.asList("car_passenger", "car_fromWDR"));
		config.qsim().setMainModes(mainModes);
		
		//Following code block was used in ASTRA2018-002 to add "truck" and "truck_av" to network modes. - cvl April 2021
		//The code block was modified to set "car_passenger" as network to try and get WDR tests to run. 
		//TODO: In future a proper solution with car_passenger as teleported mode should be found and implemented. - cvl April 2021
		
		Set<String> networkModes = new HashSet<>(config.plansCalcRoute().getNetworkModes());
		networkModes.addAll(Arrays.asList("car_passenger", "car_fromWDR"));
		config.plansCalcRoute().setNetworkModes(networkModes);
		
		//following code block was used in ASTRA2018-002, but is probably not necessary for these test runs, I hope. - cvl April 2021
		/*Set<String> analyzedModes = new HashSet<>(
				Arrays.asList(config.travelTimeCalculator().getAnalyzedModes().split(",")).stream()
						.map(String::trim).collect(Collectors.toSet()));
		analyzedModes.addAll(Arrays.asList("truck"));
		config.travelTimeCalculator().setAnalyzedModes(String.join(",", analyzedModes));
		config.travelTimeCalculator().setSeparateModes(false);
		*/
	}

	static public void updateNetwork(Scenario scenario, int year) {
		for (Link link : scenario.getNetwork().getLinks().values()) {
			if (link.getAllowedModes().contains("car")) {
				Set<String> allowedModes = new HashSet<>(link.getAllowedModes());
				allowedModes.addAll(Arrays.asList("truck", "car_passenger", "car_fromWDR"));
				link.setAllowedModes(allowedModes);
			}
		}

	}
}
