package org.forgerock.openam.auth.nodes;


import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.iplanet.sso.SSOException;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdRepoException;
import java.util.Collections;
import java.util.List;
import javax.security.auth.callback.Callback;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext.Builder;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.nodes.DeviceContextMatchNode.Config;
import org.forgerock.openam.core.CoreWrapper;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.utils.JsonValueBuilder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DeviceContextMatchNodeTest {

  @Mock
  CoreWrapper coreWrapper;

  @Mock
  AMIdentity amIdentity;

  @Mock
  Realm realm;

  @Mock
  Config config;

  @InjectMocks
  DeviceContextMatchNode node;

  @BeforeMethod
  public void setup() throws IdRepoException, SSOException {
    node = null;
    initMocks(this);
    given(realm.asDN()).willReturn("/");
    given(coreWrapper.getIdentity(anyString(), anyString())).willReturn(amIdentity);
    given(amIdentity.isExists()).willReturn(true);
    given(amIdentity.isActive()).willReturn(true);
  }

  @Test
  public void testProcessContextMatch()
      throws NodeProcessException, IdRepoException, SSOException {

    JsonValue profile = JsonValueBuilder.jsonValue().build();
    profile.put("platform", "android");

    JsonValue existing = JsonValueBuilder.jsonValue().build();
    existing.put("identifier", "testIdentifier");
    existing.put("profile", profile);


    JsonValue deviceAttributes = JsonValueBuilder.jsonValue().build();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", profile);

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.get(DeviceAttribute.PROFILE.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    when(amIdentity.getAttribute(anyString())).thenReturn(Collections.singleton(existing.toString()));
    Action result = node.process(getContext(sharedState, transientState, emptyList()));

    //Then
    assertThat(result.outcome).isEqualTo("true");
  }

  @Test
  public void testProcessContextNotMatch()
      throws NodeProcessException, IdRepoException, SSOException {

    JsonValue profile = JsonValueBuilder.jsonValue().build();
    profile.put("platform", "ios");

    JsonValue existing = JsonValueBuilder.jsonValue().build();
    existing.put("identifier", "testIdentifier");
    existing.put("profile", "test");


    JsonValue deviceAttributes = JsonValueBuilder.jsonValue().build();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", profile);

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.get(DeviceAttribute.PROFILE.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    when(amIdentity.getAttribute(anyString())).thenReturn(Collections.singleton(existing.toString()));
    Action result = node.process(getContext(sharedState, transientState, emptyList()));

    //Then
    assertThat(result.outcome).isEqualTo("false");
  }

  @Test
  public void testProcessContextNotFound()
      throws NodeProcessException, IdRepoException, SSOException {

    JsonValue profile = JsonValueBuilder.jsonValue().build();
    profile.put("platform", "ios");

    JsonValue existing = JsonValueBuilder.jsonValue().build();
    existing.put("identifier", "testIdentifier1");
    existing.put("profile", profile);


    JsonValue deviceAttributes = JsonValueBuilder.jsonValue().build();
    deviceAttributes.put("identifier", "testIdentifier2");
    deviceAttributes.put("profile", profile);

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.get(DeviceAttribute.PROFILE.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    when(amIdentity.getAttribute(anyString())).thenReturn(Collections.singleton(existing.toString()));
    Action result = node.process(getContext(sharedState, transientState, emptyList()));

    //Then
    assertThat(result.outcome).isEqualTo("false");
  }

  @Test
  public void testProcessContextNoProfile()
      throws NodeProcessException, IdRepoException, SSOException {

    JsonValue profile = JsonValueBuilder.jsonValue().build();
    profile.put("platform", "ios");

    JsonValue existing = JsonValueBuilder.jsonValue().build();
    existing.put("identifier", "testIdentifier1");

    JsonValue deviceAttributes = JsonValueBuilder.jsonValue().build();
    deviceAttributes.put("identifier", "testIdentifier2");
    deviceAttributes.put("profile", profile);

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.get(DeviceAttribute.PROFILE.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    when(amIdentity.getAttribute(anyString())).thenReturn(Collections.singleton(existing.toString()));
    Action result = node.process(getContext(sharedState, transientState, emptyList()));

    //Then
    assertThat(result.outcome).isEqualTo("false");
  }

  @Test(expectedExceptions = NodeProcessException.class)
  public void testProcessContextNoProfileInContext()
      throws NodeProcessException,IdRepoException, SSOException {

    JsonValue profile = JsonValueBuilder.jsonValue().build();
    profile.put("platform", "ios");

    JsonValue existing = JsonValueBuilder.jsonValue().build();
    existing.put("identifier", "testIdentifier1");
    existing.put("profile", profile);

    JsonValue deviceAttributes = JsonValueBuilder.jsonValue().build();
    deviceAttributes.put("identifier", "testIdentifier2");

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    node.process(getContext(sharedState, transientState, emptyList()));

  }

  @Test(expectedExceptions = NodeProcessException.class)
  public void testProcessContextNoUserInContext()
      throws NodeProcessException,IdRepoException, SSOException {

    JsonValue deviceAttributes = JsonValueBuilder.jsonValue().build();
    deviceAttributes.put("identifier", "testIdentifier2");

    JsonValue sharedState = json(object(
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    node.process(getContext(sharedState, transientState, emptyList()));

  }

  private TreeContext getContext(JsonValue sharedState, JsonValue transientState,
      List<? extends Callback> callbacks) {
    return new TreeContext(sharedState, transientState, new Builder().build(), callbacks);
  }

}