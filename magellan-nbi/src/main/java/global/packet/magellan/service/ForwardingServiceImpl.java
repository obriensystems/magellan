package global.packet.magellan.service;

import java.io.IOException;
import java.net.URI;
import java.math.BigDecimal;
//import java.net.http.Builder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.util.Random;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;


/**
 * 20210926
 * Stats
 * - 2 3610 MacMini on 1g - 2 vCores sat, 65/sec
 * curl -X GET "http://127.0.0.1:8080/nbi/forward/traffic?chaosPercentage=0.1&client=1&delay=1000&dns=34.111.243.135&iterations=1&to=80&useCaseNumber=2"
 * curl -X GET "http://127.0.0.1:8080/nbi/forward/traffic?chaosPercentage=0.1&client=1&delay=1000&dns=innova-pop.testgcp.ca&iterations=1&to=80&useCaseNumber=2"
 * 
 * 
 * @author michaelobrien
 *
 */
@Service
public class ForwardingServiceImpl implements ForwardingService {

    // TODO: turn these into a map keyed on thread
    private String useCaseNumber;
	private String dnsFrom;
	private String dnsTo;
	private String delay;
	private String portTo;
	private String portFrom;
    private double chaosPercentage;
    private String client;
	private AtomicLong counter = new AtomicLong(1);
	private HttpClient httpClient = HttpClient.newBuilder().build();
	//private static boolean stopForwarding = false;
    private AtomicLong iterations = new AtomicLong(100);
    private String region; // L1 L2
	
	
	static Logger logger = Logger.getLogger(ForwardingServiceImpl.class.getName());
	
	private Thread thread;
	private AtomicBoolean running = new AtomicBoolean(false);
	private AtomicBoolean stopped = new AtomicBoolean(true);	

    //private static final String PROTOCOL = "http";
    private static final String PROTOCOL = "https";

//  private static final String URL_RETURN = "http://host.docker.internal:8888";
//  private static final String URL_RETURN = "http://host.docker.internal:";//8080/nbi/forward/packet";
	private static final String URL_POSTFIX_REFLECTOR = "/nbi/forward/packet";
	//private static final String URL_POSTFIX_TRAFFIC = "/sbi/createEvent";///nbi/forward/traffic";
    private static final String URL_POSTFIX_TRAFFIC = "/popRequest";///nbi/forward/traffic";
    private static final String URL_POSTFIX_API = "/nbi/api";
	private Runnable runnable = () -> { sendMessage(); };
    private Runnable runnableTraffic = () -> { sendMessage(false); };
    // random string to avoid http caching
    private Random randomGenerator = new Random();
    // for simulated db failures
    private Random randomGeneratorDB = new Random();
    
    //https://stackoverflow.com/questions/52988677/allow-insecure-https-connection-for-java-jdk-11-httpclient
    private static TrustManager[] trustAllCerts = new TrustManager[]{
        new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }
            public void checkClientTrusted(
                java.security.cert.X509Certificate[] certs, String authType) {
            }
            public void checkServerTrusted(
                java.security.cert.X509Certificate[] certs, String authType) {
            }
        }
    };
    

    // debug
    //curl -X GET "http://127.0.0.1:8080/nbi/forward/traffic?delay=100&dns=traffic-generation-backend-vyua7q27tq-nn.a.run.app&iterations=10&to=80" -H "accept: */*"
	@Override
	public String reset() {
		String ret = "";
		if(running.get()) {
			running.set(false);
			stopped.set(true);
			logger.info("Stopping thread");
			//thread.interrupt();
		} else {
			running.set(true);
			stopped.set(false);
			logger.info("resuming thread on next request");
		}
		return ret;
	}


    // add random configurable (null parameter passing)
	@Override
	public String forward(String dnsFrom, String dnsTo, String from, String to, String delay, String region) {
		try { 
			Thread.sleep(Long.parseLong(delay));
			} catch (Exception e) {};
			
            this.region = region;
		portFrom = from;
		portTo = to;
		this.delay = delay;
		this.dnsFrom = dnsFrom;
		this.dnsTo = dnsTo;
		//Runnable aRunnable = runnable;
		// 1 thread at a time
		//if(!stopped.get())
			thread = new Thread(runnable);
			thread.start();	
			//running.set(true);
			//stopped.set(false);		
		return Long.toString(counter.addAndGet(1));
	}


    private void sendMessage() {
        sendMessage(true);
    }

    private void sendMessage(boolean isReflector)  {
        String url = "";//PROTOCOL + "://";
        if(isReflector) {
            url = url + dnsTo + 
				":" + portTo + URL_POSTFIX_API + //URL_POSTFIX_REFLECTOR + 
				"?dnsFrom=" + dnsTo + 
				"&dnsTo=" + dnsFrom + 
				"&from=" + portTo + 
				"&to=" + portFrom + 
				"&delay=" + delay +
                
                "&nocache=" + Integer.toString(randomGenerator.nextInt());
        } else {
            url = url + dnsTo + 
            ":" + portTo + 
            //URL_POSTFIX_TRAFFIC + //;// + //URL_POSTFIX_API + 
            URL_POSTFIX_API + 
            "?usecasename=uc" + useCaseNumber +
            "&client=" + client;
 

            //"?dns=" + dnsTo + 
            //"&to=" + portTo + 
            //"&delay=" + delay +
            //"&iterations=" + iterations.get();
        }
        sendMessage(url, isReflector);
    }

	private void sendMessage(String url, boolean isReflector) {
        //Builder HttpRequest.newBuilder();
        if(isReflector) {
    		if(!stopped.get()) {
	    		logger.info("Request reflection: " + url);
		    	HttpRequest request = HttpRequest.newBuilder()
			    	.uri(URI.create(url))
				    .GET()
				    .build();
		    	try {
			    	HttpResponse<String> response =
						httpClient.send(request, BodyHandlers.ofString());
				    String body = response.body();
				    logger.info("Response: " + body);
			    } catch (IOException ioe) {
			    	ioe.printStackTrace();
			    } catch (InterruptedException ie) {
				    ie.printStackTrace();
		    	}
		    } else {
			    logger.info("Skipping reflection");
 
	    	}
        } else {

            if(!stopped.get()) {
            // traffic generation
            //long iters = iterations.get();
            //IntStream.range(0, iters).forEach()

            //logger.info("traffic generation: " + fullUrl);
            String fullUrl;
            for(long i=0; i<iterations.get(); i++) {
                try { 
                    Thread.sleep(Long.parseLong(delay));
                    } catch (Exception e) {};
        
                    if(this.isBroken(chaosPercentage)) {
                        fullUrl = url + 
                        "&timestamp=" + System.currentTimeMillis();// + 
                  
                    } else {
                        fullUrl = url + 
                        "&requestnumber=" + i +
                        "&timestamp=" + System.currentTimeMillis() +
                        "&loadGeneratorRegionName=" + region;  
                    //"&timestampns=" + System.nanoTime() + 
                    //"&timestampms=" + System.currentTimeMillis();// + 
                    //"&nocache=" + Integer.toString(randomGenerator.nextInt());
                    }

                logger.info("Request: " + i + " of " + iterations.get() + " : at: " + System.currentTimeMillis() + " url: " + fullUrl);

            // Integer.toString(randomGenerator.nextInt()
            // System.nanoTime()
            // For POST id is autogenerated
            //String json = "{\"id\": 0,\"timestamp\": \"" + 
            //    System.nanoTime() + "\",\"state\": \"" +
            //    Integer.toString(randomGenerator.nextInt()) + "\"}";
            //logger.info("json: " + json);
            
            SSLContext sslContext = null;

            try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            } catch (Exception e) {
                
            }
            
            HttpRequest request = HttpRequest.newBuilder()
                //.uri(URI.create("http://34.110.234.112/popRequest?usecasename=uc3&requestnumber=1&timestamp=8698717501110"))//url))
                .uri(URI.create(fullUrl))
                // POST
                // fix 2022-05-30 19:46:31.967  INFO 7693 --- [     Thread-138] g.p.m.service.ForwardingServiceImpl      : Response: {"timestamp":"2022-05-30T19:46:31.948+0000","status":415,"error":"Unsupported Media Type","message":"Content type '' not supported","path":"/sbi/createEvent"}
                //.header("Content-Type", "application/json")
                //.POST(BodyPublishers.ofString(json))

                // GET
                .GET()
                //.sslContext(sslContext)
                .build();
            try {
                long startTime = System.currentTimeMillis();
                HttpClient httpClient2 = HttpClient.newBuilder().sslContext(sslContext).build();
                HttpResponse<String> response =
                    httpClient2.send(request, BodyHandlers.ofString());
                String body = response.body();
                logger.info("Response: " + body);
                long duration = System.currentTimeMillis() - startTime;
                logger.info("Request/Response duration: " + duration);
                boolean requestCommitted = false; // stub for now
                if(body.equalsIgnoreCase("ok")) {
                    requestCommitted = true;
                }

                // attempt simulated timeout
                if(this.isBroken(chaosPercentage)) {
                    body = "timeout";
                    logger.info("simulated timeout");
                    logger.info("timeout " + fullUrl + " " + 100000);
                }

                // Response (ok/dbaas/timeout) Request url and Request/Response duration
                //logger.info(" Response " + body + " Request url " + fullUrl + " and Request/Response duration " + duration);
                logger.info("DEMO LOG response: " + body + " url: " + fullUrl + " duration: " + duration);
                if(requestCommitted) {
                    logger.info("request committed " + fullUrl + " " + duration);
                } else {
                    logger.info("request not committed " + duration);
                }
            } catch (IOException ioe) {
                logger.info("request not committed ");
                logger.info("backend server exception: " + ioe.getMessage());
                ioe.printStackTrace();
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }

        }
    }
	}

    private boolean isBroken(double percentage) {
        BigDecimal subset = new BigDecimal(100000);
        subset = subset.multiply(new BigDecimal(percentage));
        //logger.info("sub: " + subset);
        BigDecimal actual = new BigDecimal(randomGeneratorDB.nextInt(100000));
        //logger.info("act: " + actual);

        if(actual.intValue() < subset.intValue()) {
            //if(randomGeneratorDB.nextInt(1) > 0) {
            logger.info("force simulated DBaaS or timeout failure");
            return true;
        } else {
            return false;
        }
    }


    @Override
	public String traffic(String useCaseNumber, String client, String chaosPercentage, String dns, String to, String delay, String iterationsString, String region) {
		try { 
			Thread.sleep(Long.parseLong(delay));
			} catch (Exception e) {};
			
            this.region = region;
		portTo = to;
        this.useCaseNumber = useCaseNumber;
        this.client = client;
        this.chaosPercentage = Double.parseDouble(chaosPercentage);
		this.delay = delay;
		this.dnsTo = dns;
        this.iterations = new AtomicLong(Long.parseLong(iterationsString));
        running.set(true);
        stopped.set(false);
		//Runnable aRunnable = runnable;
		// 1 thread at a time
		//if(!stopped.get())
			thread = new Thread(runnableTraffic);
			thread.start();	
			//running.set(true);
			//stopped.set(false);		
		return Long.toString(counter.addAndGet(1));
	}

    // swagger
    //https://traffic-generation-source-vyua7q27tq-nn.a.run.app/nbi/swagger-ui.html#/forwarding-controller/getTrafficUsingGET
    // curl -X GET "https://traffic-generation-source-vyua7q27tq-nn.a.run.app/nbi/forward/traffic?chaosPercentage=.01&client=0&delay=1&dns=34.110.234.112&iterations=1000&to=80&useCaseNumber=2" -H "accept: */*"

    // curl -X GET "http://127.0.0.1:8080/nbi/forward/traffic?useCaseNumber=3&client=1&chaosPercentage=0.01&delay=0&dns=34.110.234.112&iterations=2000&to=80" -H "accept: */*"



}
