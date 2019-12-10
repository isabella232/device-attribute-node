package org.forgerock.openam.auth.nodes;


import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.mockito.BDDMockito.given;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.List;
import javax.security.auth.callback.Callback;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext.Builder;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.nodes.DeviceJailBreakVerificationNode.Config;
import org.forgerock.openam.utils.JsonValueBuilder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DeviceJailBreakVerificationNodeTest {

  @Mock
  Config config;

  @InjectMocks
  DeviceJailBreakVerificationNode node;

  @BeforeMethod
  public void setup() {
    node = null;
    initMocks(this);
    given(config.score()).willReturn("0.5"); //min score to pass
  }

  @Test
  public void testProcessWithJailBroken()
      throws NodeProcessException {

    JsonValue profile = JsonValueBuilder.jsonValue().build();
    JsonValue platform = JsonValueBuilder.jsonValue().build();
    platform.put("jailBreakScore", 0.8);
    profile.put("platform", platform);

    JsonValue deviceAttributes = JsonValueBuilder.jsonValue().build();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", profile);

    JsonValue sharedState = json(object(
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.get(DeviceAttribute.PROFILE.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    Action result = node.process(getContext(sharedState, transientState, emptyList()));

    //Then
    assertThat(result.outcome).isEqualTo("false");
  }

  @Test
  public void testProcessWithNotJailBroken()
      throws NodeProcessException {

    JsonValue profile = JsonValueBuilder.jsonValue().build();
    JsonValue platform = JsonValueBuilder.jsonValue().build();
    platform.put("jailBreakScore", 0);
    profile.put("platform", platform);

    JsonValue deviceAttributes = JsonValueBuilder.jsonValue().build();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", profile);

    JsonValue sharedState = json(object(
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.get(DeviceAttribute.PROFILE.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    Action result = node.process(getContext(sharedState, transientState, emptyList()));

    //Then
    assertThat(result.outcome).isEqualTo("true");
  }

  @Test
  public void testProcessWithExactMatch()
      throws NodeProcessException {

    JsonValue profile = JsonValueBuilder.jsonValue().build();
    JsonValue platform = JsonValueBuilder.jsonValue().build();
    platform.put("jailBreakScore", 0.5);
    profile.put("platform", platform);

    JsonValue deviceAttributes = JsonValueBuilder.jsonValue().build();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", profile);

    JsonValue sharedState = json(object(
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.get(DeviceAttribute.PROFILE.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    Action result = node.process(getContext(sharedState, transientState, emptyList()));

    //Then
    assertThat(result.outcome).isEqualTo("true");
  }

  @Test(expectedExceptions = NodeProcessException.class)
  public void testProcessNoProfileCollected()
      throws NodeProcessException {

    JsonValue sharedState = json(object());
    JsonValue transientState = json(object());

    // When
    node.process(getContext(sharedState, transientState, emptyList()));

  }


  private TreeContext getContext(JsonValue sharedState, JsonValue transientState,
      List<? extends Callback> callbacks) {
    return new TreeContext(sharedState, transientState, new Builder().build(), callbacks);
  }

}