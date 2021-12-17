package ivt.test;

import java.util.ArrayList;
import java.util.List;

public class TestSomeListFunctions {

	public static void main(String[] args) {
		
		final List<String> listToBeModified = new ArrayList<String>();
		final String origin = "starting here";
		final List<String> listToBeInserted = new ArrayList<String>();
		final String destination = "ending here";
		
		listToBeModified.add("first thing");
		listToBeModified.add("second thing");
		listToBeModified.add("starting here");
		listToBeModified.add("original first stage");
		listToBeModified.add("original second stage");
		listToBeModified.add("original third stage");
		listToBeModified.add("ending here");
		listToBeModified.add("third thing");
		listToBeModified.add("fourth thing");
		
		listToBeInserted.add("starting here");
		listToBeInserted.add("replacement first stage");
		listToBeInserted.add("Replacement second stage");
		listToBeInserted.add("Replacement third stage");
		listToBeInserted.add("ending here");
		
		System.out.println("This is the list to be inserted now: " + listToBeInserted);
		System.out.println("This is the list to be modified now: " + listToBeModified);
		
		List<String> seq = listToBeModified.subList(3, 6);
		List<String> oldList = new ArrayList<>( seq );
		seq.clear();
		assert listToBeInserted != null;
		seq.addAll(listToBeInserted);
		
		System.out.println("This is the list to be inserted now: " + listToBeInserted);
		System.out.println("This is the list to be modified now: " + listToBeModified);
		
	}
	
	
	
}
