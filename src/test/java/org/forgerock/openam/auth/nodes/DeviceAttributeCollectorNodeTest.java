package org.forgerock.openam.auth.nodes;


import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.mockito.BDDMockito.given;
import static org.mockito.MockitoAnnotations.initMocks;

import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import java.util.List;
import javax.security.auth.callback.Callback;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext.Builder;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.nodes.DeviceAttributeCollectorNode.Config;
import org.forgerock.openam.utils.JsonValueBuilder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DeviceAttributeCollectorNodeTest {

  @Mock
  Config config;

  @InjectMocks
  DeviceAttributeCollectorNode node;

  @BeforeMethod
  public void setup() {
    node = null;
    initMocks(this);
    given(config.deviceProfile()).willReturn(true);
    given(config.devicePublicKey()).willReturn(true);
    given(config.deviceLocation()).willReturn(true);
  }

  @Test
  public void testProcessWithNoCallback()
      throws NodeProcessException {
    JsonValue sharedState = json(object());
    JsonValue transientState = json(object());

    // When
    Action result = node.process(getContext(sharedState, transientState, emptyList()));

    //Then
    assertThat(result.outcome).isNull();
    assertThat(result.callbacks).hasSize(1);
    assertThat(result.callbacks.get(0)).isInstanceOf(HiddenValueCallback.class);
    assertThat(((HiddenValueCallback) result.callbacks.get(0)).getId())
        .isEqualTo("device://forgerock?attributes=profile&attributes=publicKey&attributes=location");

  }

  @Test
  public void testProcessWithCallback()
      throws NodeProcessException {
    JsonValue sharedState = json(object(field(USERNAME, "bob")));
    JsonValue transientState = json(object());

    HiddenValueCallback hiddenValueCallback = new HiddenValueCallback(
        DeviceAttribute.PROFILE.getAttributeName());
    JsonValue profile = JsonValueBuilder.jsonValue().build();
    profile.put("identifier", "testIdentifier1");
    profile.put("profile", "{\"test\":\"value\"}");
    hiddenValueCallback.setValue(profile.toString());

    // When
    Action result = node
        .process(getContext(sharedState, transientState, singletonList(hiddenValueCallback)));

    //Then
    assertThat(result.outcome).isEqualTo("outcome");
    assertThat(result.callbacks).isEmpty();
    assertThat(result.sharedState.isDefined(DeviceAttribute.PROFILE.getVariableName())).isTrue();
    assertThat(result.sharedState.get(DeviceAttribute.PUBLIC_KEY.getVariableName())).isEmpty();

  }

  @Test
  public void testProcessWithCallbackAllAttributes()
      throws NodeProcessException  {
    JsonValue sharedState = json(object(field(USERNAME, "bob")));
    JsonValue transientState = json(object());

    HiddenValueCallback hiddenValueCallback = new HiddenValueCallback(
        DeviceAttribute.PROFILE.getAttributeName());
    JsonValue profile = JsonValueBuilder.jsonValue().build();
    profile.put("identifier", "testIdentifier1");
    profile.put("profile", "{\"test\":\"value\"}");
    profile.put("location", "{\"test\":\"value\"}");
    profile.put("publicKey", "{\"test\":\"value\"}");
    hiddenValueCallback.setValue(profile.toString());

    // When
    Action result = node
        .process(getContext(sharedState, transientState, singletonList(hiddenValueCallback)));

    //Then
    assertThat(result.outcome).isEqualTo("outcome");
    assertThat(result.callbacks).isEmpty();
    assertThat(result.sharedState.get(DeviceAttribute.IDENTIFIER.getVariableName()).getObject())
        .isNotNull();
    assertThat(result.sharedState.get(DeviceAttribute.PROFILE.getVariableName()).getObject())
        .isNotNull();
    assertThat(result.sharedState.get(DeviceAttribute.PUBLIC_KEY.getVariableName()).getObject())
        .isNotNull();
    assertThat(result.sharedState.get(DeviceAttribute.LOCATION.getVariableName()).getObject())
        .isNotNull();

  }

  private TreeContext getContext(JsonValue sharedState, JsonValue transientState,
      List<? extends Callback> callbacks) {
    return new TreeContext(sharedState, transientState, new Builder().build(), callbacks);
  }

}