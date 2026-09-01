package org.eblocker.server.http.service;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class WireGuardCustomRouteValidatorTest {

    private final WireGuardCustomRouteValidator validator =
            new WireGuardCustomRouteValidator();

    @Test
    public void normalizesAndDeduplicatesInStableInputOrder() {
        List<String> result =
                validator.normalize(
                        Arrays.asList(
                                " 192.168.5.77/24 ",
                                "10.23.45.67/8",
                                "192.168.5.0/24"
                        )
                );

        assertEquals(
                Arrays.asList(
                        "192.168.5.0/24",
                        "10.0.0.0/8"
                ),
                result
        );
    }

    @Test
    public void rejectsNullOrEmptyLists() {
        expectIllegalArgument(
                () -> validator.normalize(null)
        );

        expectIllegalArgument(
                () -> validator.normalize(
                        Collections.emptyList()
                )
        );
    }

    @Test
    public void rejectsBlankEntries() {
        expectIllegalArgument(
                () -> validator.normalize(
                        Arrays.asList(
                                "10.0.0.0/8",
                                " "
                        )
                )
        );
    }

    @Test
    public void rejectsIpv6() {
        expectIllegalArgument(
                () -> validator.normalize(
                        Collections.singletonList(
                                "2001:db8::/32"
                        )
                )
        );
    }

    @Test
    public void enforcesRouteCountLimit() {
        List<String> routes =
                new ArrayList<>();

        for (int i = 0;
             i < WireGuardCustomRouteValidator.MAX_ROUTES + 1;
             i++) {

            routes.add(
                    "10.0.0."
                            + (i % 256)
                            + "/32"
            );
        }

        expectIllegalArgument(
                () -> validator.normalize(routes)
        );
    }

    @Test
    public void enforcesTotalInputLengthLimit() {
        StringBuilder value =
                new StringBuilder(
                        "10.0.0.1/32"
                );

        while (value.length()
                <= WireGuardCustomRouteValidator
                        .MAX_TOTAL_INPUT_LENGTH) {

            value.append(' ');
        }

        expectIllegalArgument(
                () -> validator.normalize(
                        Collections.singletonList(
                                value.toString()
                        )
                )
        );
    }

    private void expectIllegalArgument(
            Runnable action) {

        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
