package io.valkey.springframework.data.valkey.connection.valkeyglide;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import glide.api.models.GlideString;

import java.util.Collection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.valkey.springframework.data.valkey.connection.MessageListener;
import io.valkey.springframework.data.valkey.connection.ValkeyInvalidSubscriptionException;
import io.valkey.springframework.data.valkey.connection.SubscriptionListener;

class ValkeyGlideSubscriptionUnitTests {

    private ValkeyGlideSubscription subscription;
    private UnifiedGlideClient client;
    private DelegatingPubSubListener pubSubListener;
    private MessageListener messageListener;

    @BeforeEach
    void setUp() throws Exception {
        client = mock(UnifiedGlideClient.class);
        pubSubListener = mock(DelegatingPubSubListener.class);
        messageListener = mock(MessageListener.class);

        // Mock customCommand to return null (no exception)
        when(client.customCommand(any(GlideString[].class))).thenReturn(null);

        subscription = new ValkeyGlideSubscription(messageListener, client, pubSubListener);
    }

    @Test
    void testUnsubscribeAllAndClose() {
        subscription.subscribe(new byte[][] { "a".getBytes() });
        subscription.unsubscribe();

        verify(pubSubListener).clearListener();

        assertThat(subscription.isAlive()).isFalse();
        assertThat(subscription.getChannels()).isEmpty();
        assertThat(subscription.getPatterns()).isEmpty();
    }

    @Test
    void testUnsubscribeAllChannelsWithPatterns() {
        subscription.subscribe(new byte[][] { "a".getBytes() });
        subscription.pSubscribe(new byte[][] { "s*".getBytes() });
        subscription.unsubscribe();

        assertThat(subscription.isAlive()).isTrue();
        assertThat(subscription.getChannels()).isEmpty();

        Collection<byte[]> patterns = subscription.getPatterns();
        assertThat(patterns).hasSize(1);
        assertThat(patterns.iterator().next()).isEqualTo("s*".getBytes());
    }

    @Test
    void testUnsubscribeChannelAndClose() {
        byte[][] channel = new byte[][] { "a".getBytes() };

        subscription.subscribe(channel);
        subscription.unsubscribe(channel);

        verify(pubSubListener).clearListener();

        assertThat(subscription.isAlive()).isFalse();
        assertThat(subscription.getChannels()).isEmpty();
        assertThat(subscription.getPatterns()).isEmpty();
    }

    @Test
    void testUnsubscribeChannelSomeLeft() {
        byte[][] channels = new byte[][] { "a".getBytes(), "b".getBytes() };

        subscription.subscribe(channels);
        subscription.unsubscribe(new byte[][] { "a".getBytes() });

        assertThat(subscription.isAlive()).isTrue();

        Collection<byte[]> subChannels = subscription.getChannels();
        assertThat(subChannels).hasSize(1);
        assertThat(subChannels.iterator().next()).isEqualTo("b".getBytes());
        assertThat(subscription.getPatterns()).isEmpty();
    }

    @Test
    void testUnsubscribeChannelWithPatterns() {
        byte[][] channel = new byte[][] { "a".getBytes() };

        subscription.subscribe(channel);
        subscription.pSubscribe(new byte[][] { "s*".getBytes() });
        subscription.unsubscribe(channel);

        assertThat(subscription.isAlive()).isTrue();
        assertThat(subscription.getChannels()).isEmpty();

        Collection<byte[]> patterns = subscription.getPatterns();
        assertThat(patterns).hasSize(1);
        assertThat(patterns.iterator().next()).isEqualTo("s*".getBytes());
    }

    @Test
    void testUnsubscribeChannelWithPatternsSomeLeft() {
        byte[][] channel = new byte[][] { "a".getBytes() };

        subscription.subscribe("a".getBytes(), "b".getBytes());
        subscription.pSubscribe(new byte[][] { "s*".getBytes() });
        subscription.unsubscribe(channel);

        assertThat(subscription.isAlive()).isTrue();

        Collection<byte[]> channels = subscription.getChannels();
        assertThat(channels).hasSize(1);
        assertThat(channels.iterator().next()).isEqualTo("b".getBytes());

        Collection<byte[]> patterns = subscription.getPatterns();
        assertThat(patterns).hasSize(1);
        assertThat(patterns.iterator().next()).isEqualTo("s*".getBytes());
    }

    @Test
    void testUnsubscribeAllNoChannels() {
        subscription.pSubscribe(new byte[][] { "s*".getBytes() });
        subscription.unsubscribe();

        assertThat(subscription.isAlive()).isTrue();
        assertThat(subscription.getChannels()).isEmpty();

        Collection<byte[]> patterns = subscription.getPatterns();
        assertThat(patterns).hasSize(1);
        assertThat(patterns.iterator().next()).isEqualTo("s*".getBytes());
    }

    @Test
    void testUnsubscribeNotAlive() {
        subscription.subscribe(new byte[][] { "a".getBytes() });
        subscription.unsubscribe();

        verify(pubSubListener).clearListener();

        assertThat(subscription.isAlive()).isFalse();

        // Calling unsubscribe again should not throw
        subscription.unsubscribe();
    }

    @Test
    void testSubscribeNotAlive() {
        subscription.subscribe(new byte[][] { "a".getBytes() });
        subscription.unsubscribe();

        assertThat(subscription.isAlive()).isFalse();

        assertThatExceptionOfType(ValkeyInvalidSubscriptionException.class)
                .isThrownBy(() -> subscription.subscribe(new byte[][] { "s".getBytes() }));
    }

    @Test
    void testPUnsubscribeAllAndClose() {
        subscription.pSubscribe(new byte[][] { "a*".getBytes() });
        subscription.pUnsubscribe();

        verify(pubSubListener).clearListener();

        assertThat(subscription.isAlive()).isFalse();
        assertThat(subscription.getChannels()).isEmpty();
        assertThat(subscription.getPatterns()).isEmpty();
    }

    @Test
    void testPUnsubscribeAllPatternsWithChannels() {
        subscription.subscribe(new byte[][] { "a".getBytes() });
        subscription.pSubscribe(new byte[][] { "s*".getBytes() });
        subscription.pUnsubscribe();

        assertThat(subscription.isAlive()).isTrue();
        assertThat(subscription.getPatterns()).isEmpty();

        Collection<byte[]> channels = subscription.getChannels();
        assertThat(channels).hasSize(1);
        assertThat(channels.iterator().next()).isEqualTo("a".getBytes());
    }

    @Test
    void testPUnsubscribeAndClose() {
        byte[][] pattern = new byte[][] { "a*".getBytes() };

        subscription.pSubscribe(pattern);
        subscription.pUnsubscribe(pattern);

        verify(pubSubListener).clearListener();

        assertThat(subscription.isAlive()).isFalse();
        assertThat(subscription.getChannels()).isEmpty();
        assertThat(subscription.getPatterns()).isEmpty();
    }

    @Test
    void testPUnsubscribePatternSomeLeft() {
        byte[][] patterns = new byte[][] { "a*".getBytes(), "b*".getBytes() };
        subscription.pSubscribe(patterns);
        subscription.pUnsubscribe(new byte[][] { "a*".getBytes() });

        assertThat(subscription.isAlive()).isTrue();

        Collection<byte[]> subPatterns = subscription.getPatterns();
        assertThat(subPatterns).hasSize(1);
        assertThat(subPatterns.iterator().next()).isEqualTo("b*".getBytes());
        assertThat(subscription.getChannels()).isEmpty();
    }

    @Test
    void testPUnsubscribePatternWithChannels() {
        byte[][] pattern = new byte[][] { "s*".getBytes() };

        subscription.subscribe(new byte[][] { "a".getBytes() });
        subscription.pSubscribe(pattern);
        subscription.pUnsubscribe(pattern);

        assertThat(subscription.isAlive()).isTrue();
        assertThat(subscription.getPatterns()).isEmpty();

        Collection<byte[]> channels = subscription.getChannels();
        assertThat(channels).hasSize(1);
        assertThat(channels.iterator().next()).isEqualTo("a".getBytes());
    }

    @Test
    void testUnsubscribePatternWithChannelsSomeLeft() {
        byte[][] pattern = new byte[][] { "a*".getBytes() };

        subscription.pSubscribe("a*".getBytes(), "b*".getBytes());
        subscription.subscribe(new byte[][] { "a".getBytes() });
        subscription.pUnsubscribe(pattern);

        assertThat(subscription.isAlive()).isTrue();

        Collection<byte[]> channels = subscription.getChannels();
        assertThat(channels).hasSize(1);
        assertThat(channels.iterator().next()).isEqualTo("a".getBytes());

        Collection<byte[]> patterns = subscription.getPatterns();
        assertThat(patterns).hasSize(1);
        assertThat(patterns.iterator().next()).isEqualTo("b*".getBytes());
    }

    @Test
    void testPUnsubscribeAllNoPatterns() {
        subscription.subscribe(new byte[][] { "s".getBytes() });
        subscription.pUnsubscribe();

        assertThat(subscription.isAlive()).isTrue();
        assertThat(subscription.getPatterns()).isEmpty();

        Collection<byte[]> channels = subscription.getChannels();
        assertThat(channels).hasSize(1);
        assertThat(channels.iterator().next()).isEqualTo("s".getBytes());
    }

    @Test
    void testPUnsubscribeNotAlive() {
        subscription.subscribe(new byte[][] { "a".getBytes() });
        subscription.unsubscribe();

        assertThat(subscription.isAlive()).isFalse();

        // Calling pUnsubscribe when not alive should not throw
        subscription.pUnsubscribe();

        verify(pubSubListener).clearListener();
    }

    @Test
    void testPSubscribeNotAlive() {
        subscription.subscribe(new byte[][] { "a".getBytes() });
        subscription.unsubscribe();

        assertThat(subscription.isAlive()).isFalse();

        assertThatExceptionOfType(ValkeyInvalidSubscriptionException.class)
                .isThrownBy(() -> subscription.pSubscribe(new byte[][] { "s*".getBytes() }));
    }

    @Test
    void testDoCloseNotSubscribed() {
        subscription.doClose();

        verify(pubSubListener).clearListener();
    }

    @Test
    void testDoCloseSubscribedChannels() throws Exception {
        subscription.subscribe(new byte[][] { "a".getBytes() });
        subscription.doClose();

        verify(pubSubListener).clearListener();
        verify(client).customCommand(argThat(args ->
            args.length >= 2 &&
            "UNSUBSCRIBE_BLOCKING".equals(args[0].getString()) &&
            "0".equals(args[args.length - 1].getString())
        ));
    }

    @Test
    void testDoCloseSubscribedPatterns() throws Exception {
        subscription.pSubscribe(new byte[][] { "a*".getBytes() });
        subscription.doClose();

        verify(pubSubListener).clearListener();
        verify(client).customCommand(argThat(args ->
            args.length >= 2 &&
            "PUNSUBSCRIBE_BLOCKING".equals(args[0].getString()) &&
            "0".equals(args[args.length - 1].getString())
        ));
    }

    @Test
    void testDoCloseSubscribedChannelsAndPatterns() throws Exception {
        subscription.subscribe(new byte[][] { "a".getBytes() });
        subscription.pSubscribe(new byte[][] { "a*".getBytes() });
        subscription.doClose();

        verify(pubSubListener).clearListener();
    }

    @Test
    void testSubscribeCallsCustomCommand() throws Exception {
        subscription.subscribe(new byte[][] { "channel1".getBytes() });

        verify(client).customCommand(argThat(args ->
            args.length == 3 &&
            "SUBSCRIBE_BLOCKING".equals(args[0].getString()) &&
            "channel1".equals(args[1].getString()) &&
            "0".equals(args[2].getString())
        ));
    }

    @Test
    void testPSubscribeCallsCustomCommand() throws Exception {
        subscription.pSubscribe(new byte[][] { "pattern*".getBytes() });

        verify(client).customCommand(argThat(args ->
            args.length == 3 &&
            "PSUBSCRIBE_BLOCKING".equals(args[0].getString()) &&
            "pattern*".equals(args[1].getString()) &&
            "0".equals(args[2].getString())
        ));
    }

    @Test
    void testSubscribeCallsSubscriptionListener() {
        MessageListener compositeListener = mock(MessageListener.class,
                withSettings().extraInterfaces(SubscriptionListener.class));

        ValkeyGlideSubscription sub = new ValkeyGlideSubscription(compositeListener, client, pubSubListener);

        sub.subscribe(new byte[][] { "channel1".getBytes() });

        verify((SubscriptionListener) compositeListener).onChannelSubscribed(eq("channel1".getBytes()), anyLong());
    }

    @Test
    void testSubscribeMultipleChannelsCallsSubscriptionListener() {
        MessageListener compositeListener = mock(MessageListener.class,
                withSettings().extraInterfaces(SubscriptionListener.class));

        ValkeyGlideSubscription sub = new ValkeyGlideSubscription(compositeListener, client, pubSubListener);

        sub.subscribe(new byte[][] { "channel1".getBytes(), "channel2".getBytes() });

        verify((SubscriptionListener) compositeListener).onChannelSubscribed(eq("channel1".getBytes()), anyLong());
        verify((SubscriptionListener) compositeListener).onChannelSubscribed(eq("channel2".getBytes()), anyLong());
    }

    @Test
    void testPSubscribeCallsSubscriptionListener() {
        MessageListener compositeListener = mock(MessageListener.class,
                withSettings().extraInterfaces(SubscriptionListener.class));

        ValkeyGlideSubscription sub = new ValkeyGlideSubscription(compositeListener, client, pubSubListener);

        sub.pSubscribe(new byte[][] { "pattern*".getBytes() });

        verify((SubscriptionListener) compositeListener).onPatternSubscribed(eq("pattern*".getBytes()), anyLong());
    }

    @Test
    void testUnsubscribeCallsSubscriptionListener() {
        MessageListener compositeListener = mock(MessageListener.class,
                withSettings().extraInterfaces(SubscriptionListener.class));

        ValkeyGlideSubscription sub = new ValkeyGlideSubscription(compositeListener, client, pubSubListener);

        sub.subscribe(new byte[][] { "channel1".getBytes(), "channel2".getBytes() });
        sub.unsubscribe(new byte[][] { "channel1".getBytes() });

        verify((SubscriptionListener) compositeListener).onChannelUnsubscribed(eq("channel1".getBytes()), anyLong());
    }

    @Test
    void testDoCloseCallsSubscriptionListenerForChannels() {
        MessageListener compositeListener = mock(MessageListener.class,
                withSettings().extraInterfaces(SubscriptionListener.class));

        ValkeyGlideSubscription sub = new ValkeyGlideSubscription(compositeListener, client, pubSubListener);

        sub.subscribe(new byte[][] { "channel1".getBytes() });
        sub.doClose();

        verify((SubscriptionListener) compositeListener).onChannelUnsubscribed(eq("channel1".getBytes()), anyLong());
    }

    @Test
    void testDoCloseCallsSubscriptionListenerForPatterns() {
        MessageListener compositeListener = mock(MessageListener.class,
                withSettings().extraInterfaces(SubscriptionListener.class));

        ValkeyGlideSubscription sub = new ValkeyGlideSubscription(compositeListener, client, pubSubListener);

        sub.pSubscribe(new byte[][] { "pattern*".getBytes() });
        sub.doClose();

        verify((SubscriptionListener) compositeListener).onPatternUnsubscribed(eq("pattern*".getBytes()), anyLong());
    }

    @Test
    void testNonSubscriptionListenerDoesNotFail() {
        MessageListener plainListener = mock(MessageListener.class);

        ValkeyGlideSubscription sub = new ValkeyGlideSubscription(plainListener, client, pubSubListener);

        sub.subscribe(new byte[][] { "channel1".getBytes() });
        sub.pSubscribe(new byte[][] { "pattern*".getBytes() });
        sub.unsubscribe(new byte[][] { "channel1".getBytes() });
        sub.pUnsubscribe(new byte[][] { "pattern*".getBytes() });
        sub.doClose();

        assertThat(sub.isAlive()).isFalse();
    }

    @Test
    void closeTwiceShouldNotFail() {
        subscription.subscribe(new byte[][] { "a".getBytes() });

        subscription.close();
        subscription.close();

        verify(pubSubListener, times(1)).clearListener();
        assertThat(subscription.isAlive()).isFalse();
    }
}
