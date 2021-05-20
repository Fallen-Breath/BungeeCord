package net.md_5.bungee.http;

import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.proxy.ProxyHandler;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.Callback;
import net.md_5.bungee.conf.YamlConfig;
import net.md_5.bungee.netty.PipelineUtils;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HttpClient
{

    public static final int TIMEOUT = 5000;
    private static final Cache<String, InetAddress> addressCache = CacheBuilder.newBuilder().expireAfterWrite( 1, TimeUnit.MINUTES ).build();

    @SuppressWarnings("UnusedAssignment")
    public static void get(String url, EventLoop eventLoop, final Callback<String> callback)
    {
        Preconditions.checkNotNull( url, "url" );
        Preconditions.checkNotNull( eventLoop, "eventLoop" );
        Preconditions.checkNotNull( callback, "callBack" );

        final URI uri = URI.create( url );

        Preconditions.checkNotNull( uri.getScheme(), "scheme" );
        Preconditions.checkNotNull( uri.getHost(), "host" );
        boolean ssl = uri.getScheme().equals( "https" );
        int port = uri.getPort();
        if ( port == -1 )
        {
            switch ( uri.getScheme() )
            {
                case "http":
                    port = 80;
                    break;
                case "https":
                    port = 443;
                    break;
                default:
                    throw new IllegalArgumentException( "Unknown scheme " + uri.getScheme() );
            }
        }

        InetAddress inetHost = addressCache.getIfPresent( uri.getHost() );
        if ( inetHost == null )
        {
            try
            {
                inetHost = InetAddress.getByName( uri.getHost() );
            } catch ( UnknownHostException ex )
            {
                callback.done( null, ex );
                return;
            }
            addressCache.put( uri.getHost(), inetHost );
        }

        Function<Callback<String>, ChannelFutureListener> futureGetter = cb -> new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception
            {
                if ( future.isSuccess() )
                {
                    String path = uri.getRawPath() + ( ( uri.getRawQuery() == null ) ? "" : "?" + uri.getRawQuery() );

                    HttpRequest request = new DefaultHttpRequest( HttpVersion.HTTP_1_1, HttpMethod.GET, path );
                    request.headers().set( HttpHeaderNames.HOST, uri.getHost() );

                    future.channel().writeAndFlush( request );
                } else
                {
                    addressCache.invalidate( uri.getHost() );
                    cb.done( null, future.cause() );
                }
            }
        };

        // auth proxy thing begins
        final ProxyHandler proxyHandler = ( (YamlConfig) BungeeCord.getInstance().getConfigurationAdapter() ).getAuthenticationProxyHandler();
        final int finalPort = port;
        final InetAddress finalInetHost = inetHost;
        final Callback<String> proxyCallback = ( result, error ) ->
        {
            // it's fine to authenticate failed with proxy, let's try again without it
            if ( error != null && proxyHandler != null )
            {
                BungeeCord.getInstance().getLogger().log( Level.SEVERE, "Error authenticating with proxy, try without", error );

                // try without
                new Bootstrap().channel( PipelineUtils.getChannel( null ) ).group( eventLoop ).
                        handler( new HttpInitializer( callback, ssl, uri.getHost(), finalPort, null ) ).
                        option( ChannelOption.CONNECT_TIMEOUT_MILLIS, TIMEOUT ).remoteAddress( finalInetHost, finalPort ).connect().addListener( futureGetter.apply( callback ) );
            } else
            {
                // no error or no proxy, pass it to the original callback
                callback.done( result, error );
            }
        };
        // auth proxy thing ends

        new Bootstrap().channel( PipelineUtils.getChannel( null ) ).group( eventLoop ).
                handler( new HttpInitializer( proxyCallback, ssl, uri.getHost(), port, proxyHandler ) ).
                option( ChannelOption.CONNECT_TIMEOUT_MILLIS, TIMEOUT ).remoteAddress( inetHost, port ).connect().addListener( futureGetter.apply( proxyCallback ) );
    }
}
