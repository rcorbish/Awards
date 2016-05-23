package com.rc;

import static spark.Spark.before;
import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.patch;
import static spark.Spark.port;
import static spark.Spark.post;
import static spark.Spark.staticFiles;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.pac4j.core.client.Clients;
import org.pac4j.core.config.Config;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.http.client.indirect.IndirectBasicAuthClient;
import org.pac4j.http.credentials.UsernamePasswordCredentials;
import org.pac4j.http.credentials.authenticator.UsernamePasswordAuthenticator;
import org.pac4j.http.credentials.authenticator.test.SimpleTestUsernamePasswordAuthenticator;
import org.pac4j.http.profile.HttpProfile;
import org.pac4j.sparkjava.CallbackRoute;
import org.pac4j.sparkjava.DefaultHttpActionAdapter;
import org.pac4j.sparkjava.RequiresAuthenticationFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.neuland.jade4j.JadeConfiguration;
import de.neuland.jade4j.template.ClasspathTemplateLoader;
import de.neuland.jade4j.template.TemplateLoader;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.template.jade.JadeTemplateEngine;

public class Run {

	final Map<UUID,Nomination> nominations ;
	final Path nominationDataDirectory ;
	final ObjectMapper mapper; 
	final JadeConfiguration jadeConfig ;
	
	public Run( Path nominationDataDirectory ) throws IOException {
		port( 6502 ) ;
		nominations = new LinkedHashMap<>() ;
		this.nominationDataDirectory = nominationDataDirectory;
		mapper = new ObjectMapper() ;
		
		TemplateLoader templateLoader = new ClasspathTemplateLoader();
		jadeConfig = new JadeConfiguration() ;
		jadeConfig.setCaching( false ) ;
		jadeConfig.setTemplateLoader(templateLoader);
		
		if( !Files.isDirectory( nominationDataDirectory ) ) {
			if( Files.exists( nominationDataDirectory ) ) {
				throw new IOException( "The nomination path given '" + nominationDataDirectory + "' is a file. It must be a directory." ) ;
			}
			nominationDataDirectory = Files.createDirectories(nominationDataDirectory) ;
		}

		Files.list( nominationDataDirectory )
		.filter( p -> Files.isReadable(p) && Files.isRegularFile(p) )	// only want readable simple files
		.forEach( this::readNomination )
		;
	}


	public void run() throws IOException {
		staticFiles.location(".");
		staticFiles.expireTime(600);

		exception( Exception.class, (exception, request, response) -> {
			response.body( "<pre>" + exception.toString() + "</pre>" ) ; 
		});
		
		final IndirectBasicAuthClient basicAuthClient = new IndirectBasicAuthClient();
		basicAuthClient.setAuthenticator( new SimpleAuthenticator( Paths.get("pwds") ) );
		basicAuthClient.setName( "Basic");
		final Clients clients = new Clients( "/login", basicAuthClient ) ;
		final Config config = new Config(clients);
		config.setHttpActionAdapter(new DefaultHttpActionAdapter() );
		
//		before("/list", new RequiresAuthenticationFilter(config, "Basic"));
//		before("/vote", new RequiresAuthenticationFilter(config, "Basic"));
		before("/*", new RequiresAuthenticationFilter(config, "Basic"));
		
		final Route callback = new CallbackRoute(config);		
		get( "/login", callback) ;
		
		get( "/", this::home, new JadeTemplateEngine( jadeConfig ) ) ;
		get( "/list", this::listNominations, new JadeTemplateEngine( jadeConfig ) ) ;
		get( "/vote", this::vote, new JadeTemplateEngine( jadeConfig ) ) ;		
		get( "/nominate", this::nominate, new JadeTemplateEngine( jadeConfig ) ) ;		
		
		post( "/submit-nomination", this::submitNomination, new JadeTemplateEngine( jadeConfig ) ) ;
		patch( "/approve", this::approve ) ;
		patch( "/unapprove", this::unapprove ) ;
		patch( "/place-vote", this::placeVote ) ;
		
		before((request,response)->{
	        // Allow cross site access to our services			
	        response.header("Access-Control-Allow-Origin", "*");
		});
	}


	public ModelAndView home( Request request, Response response ) throws Exception {
		Map<String,Object> model = new HashMap<>() ;
		model.put( "authorization",  request.headers( "Authorization" ) ) ; 		
		model.put( "cookie",  request.headers( "Cookie" ) ) ; 
		model.put( "user", getUsernameFromRequest(request) ) ;
		return new ModelAndView( model, "templates/index" )  ;
	}

	public ModelAndView nominate( Request request, Response response ) throws Exception {
		Map<String,Object> model = new HashMap<>() ;
		model.put( "authorization",  request.headers( "Authorization" ) ) ; 		
		model.put( "cookie",  request.headers( "Cookie" ) ) ; 
		model.put( "user", getUsernameFromRequest(request) ) ;
		return new ModelAndView( model, "templates/nomination" )  ;
	}

	public ModelAndView listNominations( Request request, Response response ) throws Exception {
		Map<String,Object> model = new HashMap<>() ;
		model.put( "nominations", nominations.values() ) ;
		model.put( "authorization",  request.headers( "Authorization" ) ) ; 		
		model.put( "cookie",  request.headers( "Cookie" ) ) ; 
		model.put( "user", getUsernameFromRequest(request) ) ;
		return new ModelAndView( model, "templates/listNominations" )  ;
	}

	public ModelAndView vote( Request request, Response response ) throws Exception {
		Map<String,Object> model = new HashMap<>() ;
		model.put( "nominations", nominations.values() ) ;
		model.put( "authorization",  request.headers( "Authorization" ) ) ; 
		model.put( "cookie",  request.headers( "Cookie" ) ) ; 
		model.put( "user", getUsernameFromRequest(request) ) ;
		return new ModelAndView( model, "templates/vote" )  ;
	}

	
	public Object approve( Request request, Response response ) throws Exception {
		String id = request.body() ;
		UUID uuid = UUID.fromString(id) ;
		Nomination n = nominations.get( uuid ) ;
		if( !n.approved ) {
			n.approved = true ;
			writeNomination(nominationDataDirectory, uuid);
		}
		return "Y" ;
	}
	
	public Object unapprove( Request request, Response response ) throws Exception {
		String id = request.body() ;
		UUID uuid = UUID.fromString(id) ;
		Nomination n = nominations.get( uuid ) ;
		if( n.approved ) {
			n.approved = false ;
			writeNomination(nominationDataDirectory, uuid);
		}
		return " " ;
	}

	public Object placeVote( Request request, Response response ) throws Exception {
		String bdy = request.body() ;
		String elems[] = bdy.split( "\n" ) ;
		UUID uuid = UUID.fromString(elems[0]) ;
		
		Nomination nomination = nominations.get( uuid ) ;
		nomination.votes.put( elems[2], elems[1] ) ;

		writeNomination(nominationDataDirectory, nomination.id );

		return nomination.getVotesBy( elems[2] ) ;
	}


	
	public ModelAndView submitNomination( Request request, Response response ) throws Exception {
		Nomination nomination = new Nomination() ;
		nomination.nominator = request.queryParams( "nominator" ) ; 
		nomination.nominee = request.queryParams( "nominee" ) ;
		nomination.reason = request.queryParams( "reason" ) ;
		nomination.scope = request.queryParams( "scope" ) ;
		nomination.supportingText = request.queryParams( "supportingText" ) ;
		nomination.details = request.queryParams( "details" ) ;
		
		nominations.put(nomination.id, nomination ) ;
		writeNomination(nominationDataDirectory, nomination.id );
		Map<String,Object> model = new HashMap<>() ;
		model.put("nomination", nomination ) ;
		return new ModelAndView( model, "templates/submit-nominatione" )  ;
	}

	
	
	public void readNomination( Path nominationData ) {
		try( Reader r = Files.newBufferedReader(nominationData) ) {
			Nomination nomination = mapper.readValue( r, Nomination.class ) ;
			nominations.put( nomination.id, nomination ) ;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void writeNomination( Path nominationDataDirectory, UUID id ) {
		Path dataFile = nominationDataDirectory.resolve( id.toString() ) ;
		try { 
			try( Writer w = Files.newBufferedWriter(dataFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE ) ) {
				Nomination nomination = nominations.get( id ) ;
				mapper.writeValue(w, nomination ) ;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	protected String getUsernameFromRequest( Request request ) {
		String h = request.headers( "Authorization" ) ;
		if( h==null ) return null ;
		String b64 = h.replace( "Basic ", "" ) ;
		String up = new String( Base64.getDecoder().decode( b64 ) ) ; 
		int ix = up.indexOf( ":" ) ;
		if( ix < 1 ) return null ;
		return up.substring(0,ix) ;
	}
}



class SimpleAuthenticator implements UsernamePasswordAuthenticator {

    protected static final Logger logger = LoggerFactory.getLogger(SimpleTestUsernamePasswordAuthenticator.class);
    
    private Map<String, String> pwds ;
    private Set<String> admins ;
    
    public SimpleAuthenticator( Path logins ) throws IOException {
    	pwds = new HashMap<>() ;
    	admins = new HashSet<>() ;
    	
    	Files.lines(logins)
    	.map( s -> s.trim() )
    	.filter( s -> s.length()>0 && s.charAt(0)!='#' )
    	.map( s -> s.split( "," ) )
    	.filter( s -> s.length>1 ) 
    	.forEach( s -> { pwds.put( s[0],  s[1] ); if(s.length>2 && s[2].equalsIgnoreCase("admin") ) admins.add( s[0] ) ;}  )
    	;
	}
    
    @Override
    public void validate(final UsernamePasswordCredentials credentials) {
        if (credentials == null) {
            throwsException("No credential");
        }
        String username = credentials.getUsername();
        String password = credentials.getPassword();
        if (CommonHelper.isBlank(username)) {
            throwsException("Username cannot be blank");
        }
        if (CommonHelper.isBlank(password)) {
            throwsException("Password cannot be blank");
        }
        String storedpwd = pwds.get(username) ;
        if( storedpwd == null ) {
            throwsException("Unrecognized user");
        }
        if( !storedpwd.equals(password)) {
            throwsException("Username : '" + username + "' does not match password");
        }
        final HttpProfile profile = new HttpProfile();
        profile.setId(username);
        profile.addAttribute(CommonProfile.USERNAME, username);
        profile.addAttribute("ADMIN", admins.contains(username) );
        credentials.setUserProfile(profile);
    }

    protected void throwsException(final String message) {
        throw new CredentialsException(message);
    }
}

