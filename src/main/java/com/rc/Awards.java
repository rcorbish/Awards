package com.rc;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Awards {

	public static void main( String args[] ) {
		try {
			Path nominationData = Paths.get( args.length>1 ? args[0] : "Nominations.dat" ) ;
			Run run = new Run( nominationData ) ;
			run.run() ;
		} catch( Throwable t ) {
			t.printStackTrace(); 
			System.exit( -1 ) ;
		}
	}
}
