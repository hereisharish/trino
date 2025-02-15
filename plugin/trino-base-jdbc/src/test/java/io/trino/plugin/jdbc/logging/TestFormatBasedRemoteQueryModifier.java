/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.jdbc.logging;

import io.trino.spi.TrinoException;
import io.trino.spi.security.ConnectorIdentity;
import io.trino.testing.TestingConnectorSession;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestFormatBasedRemoteQueryModifier
{
    @Test
    public void testCreatingCommentToAppendBasedOnFormatAndConnectorSession()
    {
        TestingConnectorSession connectorSession = TestingConnectorSession.builder()
                .setTraceToken("trace_token")
                .setSource("source")
                .setIdentity(ConnectorIdentity.ofUser("Alice"))
                .build();

        FormatBasedRemoteQueryModifier modifier = createRemoteQueryModifier("Query=$QUERY_ID Execution for user=$USER with source=$SOURCE ttoken=$TRACE_TOKEN");
        String modifiedQuery = modifier.apply(connectorSession, "SELECT * from USERS");

        assertThat(modifiedQuery)
                .isEqualTo("SELECT * from USERS /*Query=%s Execution for user=%s with source=%s ttoken=%s*/", connectorSession.getQueryId(), "Alice", "source", "trace_token");
    }

    @Test
    public void testCreatingCommentWithDuplicatedPredefinedValues()
    {
        TestingConnectorSession connectorSession = TestingConnectorSession.builder()
                .setTraceToken("trace_token")
                .setSource("source")
                .setIdentity(ConnectorIdentity.ofUser("Alice"))
                .build();

        FormatBasedRemoteQueryModifier modifier = createRemoteQueryModifier("$QUERY_ID, $QUERY_ID, $QUERY_ID, $QUERY_ID, $USER, $USER, $SOURCE, $SOURCE, $SOURCE, $TRACE_TOKEN, $TRACE_TOKEN");
        String modifiedQuery = modifier.apply(connectorSession, "SELECT * from USERS");

        assertThat(modifiedQuery)
                .isEqualTo("SELECT * from USERS /*%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s*/",
                        connectorSession.getQueryId(),
                        connectorSession.getQueryId(),
                        connectorSession.getQueryId(),
                        connectorSession.getQueryId(),
                        "Alice",
                        "Alice",
                        "source",
                        "source",
                        "source",
                        "trace_token",
                        "trace_token");
    }

    @Test
    public void testForSQLInjectionsByTraceToken()
    {
        TestingConnectorSession connectorSession = TestingConnectorSession.builder()
                .setTraceToken("*/; DROP TABLE TABLE_A; /*")
                .setSource("source")
                .setIdentity(ConnectorIdentity.ofUser("Alice"))
                .build();

        FormatBasedRemoteQueryModifier modifier = createRemoteQueryModifier("Query=$QUERY_ID Execution for user=$USER with source=$SOURCE ttoken=$TRACE_TOKEN");

        assertThatThrownBy(() -> modifier.apply(connectorSession, "SELECT * from USERS"))
                .isInstanceOf(TrinoException.class)
                .hasMessage("Passed value */; DROP TABLE TABLE_A; /* as $TRACE_TOKEN does not meet security criteria. It can contain only letters, digits and underscores");
    }

    @Test
    public void testForSQLInjectionsBySource()
    {
        TestingConnectorSession connectorSession = TestingConnectorSession.builder()
                .setTraceToken("trace_token")
                .setSource("*/; DROP TABLE TABLE_A; /*")
                .setIdentity(ConnectorIdentity.ofUser("Alice"))
                .build();

        FormatBasedRemoteQueryModifier modifier = createRemoteQueryModifier("Query=$QUERY_ID Execution for user=$USER with source=$SOURCE ttoken=$TRACE_TOKEN");

        assertThatThrownBy(() -> modifier.apply(connectorSession, "SELECT * from USERS"))
                .isInstanceOf(TrinoException.class)
                .hasMessage("Passed value */; DROP TABLE TABLE_A; /* as $SOURCE does not meet security criteria. It can contain only letters, digits and underscores");
    }

    @Test
    public void testFormatWithEmptyValues()
    {
        TestingConnectorSession connectorSession = TestingConnectorSession.builder()
                .setIdentity(ConnectorIdentity.ofUser("Alice"))
                .setSource("")
                .build();

        FormatBasedRemoteQueryModifier modifier = createRemoteQueryModifier("source=$SOURCE ttoken=$TRACE_TOKEN");

        String modifiedQuery = modifier.apply(connectorSession, "SELECT * FROM USERS");

        assertThat(modifiedQuery)
                .isEqualTo("SELECT * FROM USERS /*source= ttoken=*/");
    }

    private static FormatBasedRemoteQueryModifier createRemoteQueryModifier(String commentFormat)
    {
        return new FormatBasedRemoteQueryModifier(new FormatBasedRemoteQueryModifierConfig().setFormat(commentFormat));
    }
}
