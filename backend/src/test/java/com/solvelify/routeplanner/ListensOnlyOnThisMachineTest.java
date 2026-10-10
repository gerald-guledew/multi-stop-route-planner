package com.solvelify.routeplanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The app has no login, so which addresses it listens on is the only lock it has. Started for
 * real, on a real port, it must answer the machine it runs on and nothing else.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ListensOnlyOnThisMachineTest {

    private static final int PATIENCE_MILLIS = 1000;

    @LocalServerPort
    private int port;

    @Test
    void answersTheMachineItRunsOn() throws IOException {
        assertThat(accepts(InetAddress.getByName("127.0.0.1"))).isTrue();
    }

    @Test
    void answersOnNoAddressAnotherDeviceCouldReach() throws IOException {
        List<InetAddress> everyOtherAddress = NetworkInterface.networkInterfaces()
                .flatMap(NetworkInterface::inetAddresses)
                .filter(address -> !address.isLoopbackAddress())
                .toList();
        assumeFalse(everyOtherAddress.isEmpty(), "This machine is on no network, so there is nothing to try");

        // All at once. Some addresses do not refuse a connection, they just never answer.
        List<InetAddress> answered = everyOtherAddress.parallelStream().filter(this::accepts).toList();

        assertThat(answered).isEmpty();
    }

    private boolean accepts(InetAddress address) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), PATIENCE_MILLIS);
            return true;
        } catch (IOException refused) {
            return false;
        }
    }
}
