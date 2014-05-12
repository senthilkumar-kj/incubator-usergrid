package org.apache.usergrid.chop.webapp.coordinator.rest;


import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.usergrid.chop.api.CoordinatorFig;
import org.apache.usergrid.chop.stack.CoordinatedStack;
import org.apache.usergrid.chop.stack.ICoordinatedCluster;
import org.apache.usergrid.chop.stack.Instance;
import org.apache.usergrid.chop.stack.SetupStackState;
import org.apache.usergrid.chop.webapp.coordinator.StackCoordinator;

import com.google.inject.Inject;
import com.google.inject.Singleton;


/**
 * A resource that uses path parameters to expose properties for a stack build. This will be
 * used when starting runners to have them pick up properties needed to hit stack clusters.
 * Note that this is used with the archaius configuration property:
 *
 * -Darchaius.deployment.environment=CHOP
 * -Darchaius.deployment.region=${region}
 * -Darchaius.deployment.datacenter=${}
 * -Darchaius.deployment.applicationId=chop-runner
 * -Darchaius.deployment.stack=${stackId}
 * -Darchaius.configurationSource.additionalUrls=
 *    https://${endpoint}:${port}/properties/${user}/${groupId}/${artifactId}/${version}/${commitId}
 */
@Singleton
@Produces( MediaType.TEXT_PLAIN )
@Path( CoordinatorFig.PROPERTIES_PATH_DEFAULT )
public class PropertiesResource {

    private static final char DELIMITER = ' ';
    @Inject
    StackCoordinator coordinator;


    @GET
    @Path( "{user}/{groupId}/{artifactId}/{version}/{commitId}" )
    public Response getProperties(
            @PathParam( "user" ) String user,
            @PathParam( "groupId" ) String groupId,
            @PathParam( "artifactId" ) String artifactId,
            @PathParam( "version" ) String version,
            @PathParam( "commitId" ) String commitId
                                      ) throws IOException {
        // Get the stack matching these parameters
        CoordinatedStack stack = coordinator.findCoordinatedStack( commitId, artifactId, groupId, version, user );
        if( stack == null ) {
            return Response.ok().entity( "No stack could be found that matches given parameters" ).build();
        }
        else if( stack.getSetupState() != SetupStackState.SetUp ) {
            return Response.ok().entity( "Stack's setup state is: " + stack.getSetupState() ).build();
        }

        StringWriter writer = new StringWriter();
        Properties properties = new Properties();

        // flatten out and set the stack properties here before storing
        for ( ICoordinatedCluster cluster : stack.getClusters() ) {
            String keyBase = cluster.getName() + ".cluster";
            StringBuilder privateAddresses = new StringBuilder();
            StringBuilder publicAddresses = new StringBuilder();
            StringBuilder privateHostnames = new StringBuilder();
            StringBuilder publicHostnames = new StringBuilder();

            for ( Instance instance : cluster.getInstances() ) {
                privateAddresses.append( instance.getPrivateIpAddress() ).append( DELIMITER );
                publicAddresses.append( instance.getPublicIpAddress() ).append( DELIMITER );
                privateHostnames.append( instance.getPrivateDnsName() ).append( DELIMITER );
                publicHostnames.append( instance.getPublicDnsName() ).append( DELIMITER );
            }

            properties.setProperty( keyBase + ".private.addresses", privateAddresses.toString() );
            properties.setProperty( keyBase + ".public.addresses", publicAddresses.toString() );
            properties.setProperty( keyBase + ".private.hostnames", privateHostnames.toString() );
            properties.setProperty( keyBase + ".public.hostnames", publicHostnames.toString() );
        }

        properties.store( writer, "Generated by ChopUI" );

        return Response.ok().entity( writer.toString() ).build();
    }
}
