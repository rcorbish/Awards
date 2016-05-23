package com.rc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class Nomination {

	public UUID id ;
	private LocalDateTime when ;
	public String nominee ;
	public String nominator ;
	public String scope ;
	public String reason ;
	public String supportingText ;
	public String details ;
	public Map<String,String> votes ;
	
	
	public boolean approved ;
	
	public Nomination() {
		id = UUID.randomUUID() ;
		when = LocalDateTime.now();
		votes = new HashMap<>() ;
	}
	
	public String getWhen() {
		DateTimeFormatter dft = DateTimeFormatter.ISO_DATE_TIME ;
		return dft.format( when );
	}
	public void setWhen( String when ) {
		DateTimeFormatter dft = DateTimeFormatter.ISO_DATE_TIME ;
		this.when =  LocalDateTime.parse(when, dft);
	}

	@JsonIgnore
	public int getTotalVotes() {
		int rc = 0 ;
		for( String vote : votes.values() ) {
			rc += Integer.parseInt( vote ) ;
		}
		return rc ;
	}

	@JsonIgnore
	public int getVotesBy( String user ) {
		String vote = votes.get( user ) ;
		return vote==null ? 0 : Integer.parseInt( vote ) ;
	}

}
