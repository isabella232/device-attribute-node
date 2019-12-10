package org.forgerock.openam.auth.nodes;


import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.forgerock.json.JsonValue.field;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;
import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.iplanet.sso.SSOException;
import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdRepoException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.security.auth.callback.Callback;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.ExternalRequestContext.Builder;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.auth.nodes.DeviceAttributeStoreNode.Config;
import org.forgerock.openam.core.CoreWrapper;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.utils.JsonValueBuilder;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DeviceAttributeStoreNodeTest {

  @Mock
  CoreWrapper coreWrapper;

  @Mock
  AMIdentity amIdentity;

  @Mock
  Realm realm;

  @Mock
  Config config;

  @InjectMocks
  DeviceAttributeStoreNode node;

  @BeforeMethod
  public void setup() throws Exception {
    node = null;
    initMocks(this);
    given(config.deviceAttributes()).willReturn(Arrays
        .asList(DeviceAttribute.PROFILE.name(), DeviceAttribute.PUBLIC_KEY.name(),
            DeviceAttribute.LOCATION.name(), "undefined"));
    given(realm.asDN()).willReturn("/");
    given(coreWrapper.getIdentity(anyString(), anyString())).willReturn(amIdentity);
    given(amIdentity.isExists()).willReturn(true);
    given(amIdentity.isActive()).willReturn(true);
  }

  @Test(expectedExceptions = NodeProcessException.class, expectedExceptionsMessageRegExp = "Could not get a valid username from the context")
  public void testProcessWithNoUsername() throws NodeProcessException {
    JsonValue sharedState = json(object());
    JsonValue transientState = json(object());

    node.process(getContext(sharedState, transientState, emptyList()));
  }

  @Test(expectedExceptions = NodeProcessException.class, expectedExceptionsMessageRegExp = "User does not exist or inactive")
  public void testProcessWithUserNotActive()
      throws NodeProcessException, IdRepoException, SSOException {
    JsonValue sharedState = json(object(field(USERNAME, "bob")));
    JsonValue transientState = json(object());
    given(amIdentity.isActive()).willReturn(false);

    node.process(getContext(sharedState, transientState, emptyList()));
  }

  @Test(description = "No Identifier", expectedExceptions = NodeProcessException.class)
  public void testProcessWithoutIdentifier()
      throws NodeProcessException, IdRepoException, SSOException {
    JsonValue deviceAttributes = JsonValueBuilder.jsonValue().build();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", JsonValueBuilder.toJsonValue("{\"test\":\"value\"}"));
    deviceAttributes.put("publicKey", JsonValueBuilder.toJsonValue("{\"test\":\"value\"}"));
    deviceAttributes.put("location", JsonValueBuilder.toJsonValue("{\"test\":\"value\"}"));

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.get(DeviceAttribute.PROFILE.getAttributeName())),
        field(DeviceAttribute.PUBLIC_KEY.getVariableName(),
            deviceAttributes.get(DeviceAttribute.PUBLIC_KEY.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            deviceAttributes.get(DeviceAttribute.LOCATION.getAttributeName()))
    ));
    JsonValue transientState = json(object());

    HiddenValueCallback hiddenValueCallback = new HiddenValueCallback("profile publicKey location");
    hiddenValueCallback.setValue(deviceAttributes.toString());

    // When
    given(amIdentity.isActive()).willReturn(true);
    node.process(getContext(sharedState, transientState, singletonList(hiddenValueCallback)));

  }

  @Test(description = "Variable not exists in TreeContext")
  public void testProcessWithNotExistsInTreeContext()
      throws NodeProcessException, IdRepoException, SSOException {
    JsonValue deviceAttributes = JsonValueBuilder.jsonValue().build();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", JsonValueBuilder.toJsonValue("{\"test\":\"value\"}"));

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.get(DeviceAttribute.PROFILE.getAttributeName()))
    ));
    JsonValue transientState = json(object());

    HiddenValueCallback hiddenValueCallback = new HiddenValueCallback("profile publicKey location");
    hiddenValueCallback.setValue(deviceAttributes.toString());

    ArgumentCaptor<Map<String, Set>> captor = ArgumentCaptor.forClass(Map.class);
    amIdentity.setAttributes(captor.capture());

    // When
    given(amIdentity.isActive()).willReturn(true);
    when(amIdentity.getAttribute(anyString())).thenReturn(singleton(deviceAttributes.toString()));
    doNothing().when(amIdentity).setAttributes(captor.capture());
    Action result = node
        .process(getContext(sharedState, transientState, singletonList(hiddenValueCallback)));

    //Then
    assertThat(result.outcome).isEqualTo("outcome");
    assertThat(result.callbacks).isEmpty();
    //Make sure set Attribute contains the profile from the Callback
    assertThat(captor.getValue().get("deviceAttributes")).hasSize(1);
    assertThat(captor.getValue().get("deviceAttributes")).contains(deviceAttributes.toString());

  }

  @Test(description = "Update existing device attributes")
  public void testProcessWithCallbackWithUpdate()
      throws NodeProcessException, IdRepoException, SSOException {
    JsonValue deviceAttributes = JsonValueBuilder.jsonValue().build();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", JsonValueBuilder.toJsonValue("{\"test\":\"value\"}"));
    deviceAttributes.put("publicKey", JsonValueBuilder.toJsonValue("{\"test\":\"value\"}"));
    deviceAttributes.put("location", JsonValueBuilder.toJsonValue("{\"test\":\"value\"}"));

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.get(DeviceAttribute.PROFILE.getAttributeName())),
        field(DeviceAttribute.PUBLIC_KEY.getVariableName(),
            deviceAttributes.get(DeviceAttribute.PUBLIC_KEY.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            deviceAttributes.get(DeviceAttribute.LOCATION.getAttributeName()))
    ));
    JsonValue transientState = json(object());

    HiddenValueCallback hiddenValueCallback = new HiddenValueCallback("profile");
    hiddenValueCallback.setValue(deviceAttributes.toString());

    ArgumentCaptor<Map<String, Set>> captor = ArgumentCaptor.forClass(Map.class);
    amIdentity.setAttributes(captor.capture());

    // When
    given(amIdentity.isActive()).willReturn(true);
    when(amIdentity.getAttribute(anyString())).thenReturn(singleton(deviceAttributes.toString()));
    doNothing().when(amIdentity).setAttributes(captor.capture());
    Action result = node
        .process(getContext(sharedState, transientState, singletonList(hiddenValueCallback)));

    //Then
    assertThat(result.outcome).isEqualTo("outcome");
    assertThat(result.callbacks).isEmpty();
    //Make sure set Attribute contains the profile from the Callback
    assertThat(captor.getValue().get("deviceAttributes")).hasSize(1);
    assertThat(captor.getValue().get("deviceAttributes")).contains(deviceAttributes.toString());

  }

  @Test(description = "Keep the existing profile and create new profile")
  public void testProcessWithCallbackWithCreate()
      throws NodeProcessException, IdRepoException, SSOException {
    JsonValue collected = JsonValueBuilder.jsonValue().build();
    collected.put("identifier", "testIdentifier");
    collected.put("profile", JsonValueBuilder.toJsonValue("{\"test\":\"value\"}"));
    collected.put("publicKey", JsonValueBuilder.toJsonValue("{\"test\":\"value\"}"));
    collected.put("location", JsonValueBuilder.toJsonValue("{\"test\":\"value\"}"));

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            collected.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.PROFILE.getVariableName(),
            collected.get(DeviceAttribute.PROFILE.getAttributeName())),
        field(DeviceAttribute.PUBLIC_KEY.getVariableName(),
            collected.get(DeviceAttribute.PUBLIC_KEY.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            collected.get(DeviceAttribute.LOCATION.getAttributeName()))
    ));
    JsonValue transientState = json(object());

    HiddenValueCallback hiddenValueCallback = new HiddenValueCallback("profile publicKey location");
    hiddenValueCallback.setValue(collected.toString());

    JsonValue existingProfile = JsonValueBuilder.jsonValue().build();
    existingProfile.put("identifier", "testIdentifier1");

    ArgumentCaptor<Map<String, Set>> captor = ArgumentCaptor.forClass(Map.class);
    amIdentity.setAttributes(captor.capture());

    // When
    given(amIdentity.isActive()).willReturn(true);
    //Return the existing one with different identifier
    when(amIdentity.getAttribute(anyString())).thenReturn(singleton(existingProfile.toString()));
    doNothing().when(amIdentity).setAttributes(captor.capture());
    Action result = node
        .process(getContext(sharedState, transientState, singletonList(hiddenValueCallback)));

    //Then
    assertThat(result.outcome).isEqualTo("outcome");
    assertThat(result.callbacks).isEmpty();
    //Make sure set Attribute contains the profile from the Callback
    assertThat(captor.getValue().get("deviceAttributes")).contains(existingProfile.toString());
    assertThat(captor.getValue().get("deviceAttributes")).contains(collected.toString());

  }

  @Test(description = "SharedState has string instead of JSONObject")
  public void testProcessWithInvalidJsonFormatSharedState()
      throws NodeProcessException, IdRepoException, SSOException {
    JsonValue deviceAttributes = JsonValueBuilder.jsonValue().build();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", "myProfile");

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.PROFILE.getVariableName(), "myProfile")));
    JsonValue transientState = json(object());

    HiddenValueCallback hiddenValueCallback = new HiddenValueCallback("profile publicKey location");
    hiddenValueCallback.setValue(deviceAttributes.toString());

    ArgumentCaptor<Map<String, Set>> captor = ArgumentCaptor.forClass(Map.class);
    amIdentity.setAttributes(captor.capture());

    // When
    given(amIdentity.isActive()).willReturn(true);
    when(amIdentity.getAttribute(anyString())).thenReturn(new HashSet<>());
    doNothing().when(amIdentity).setAttributes(captor.capture());
    Action result = node
        .process(getContext(sharedState, transientState, singletonList(hiddenValueCallback)));

    //Then
    assertThat(result.outcome).isEqualTo("outcome");
    assertThat(result.callbacks).isEmpty();
    //Make sure set Attribute contains the profile from the Callback
    assertThat(captor.getValue().get("deviceAttributes")).contains(deviceAttributes.toString());

  }

  @Test(description = "Save Failed", expectedExceptions = NodeProcessException.class)
  public void testProcessWithSaveFailed()
      throws NodeProcessException, IdRepoException, SSOException {
    JsonValue deviceAttributes = JsonValueBuilder.jsonValue().build();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", JsonValueBuilder.toJsonValue("{\"test\":\"value\"}"));
    deviceAttributes.put("publicKey", JsonValueBuilder.toJsonValue("{\"test\":\"value\"}"));
    deviceAttributes.put("location", JsonValueBuilder.toJsonValue("{\"test\":\"value\"}"));

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.get(DeviceAttribute.PROFILE.getAttributeName())),
        field(DeviceAttribute.PUBLIC_KEY.getVariableName(),
            deviceAttributes.get(DeviceAttribute.PUBLIC_KEY.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            deviceAttributes.get(DeviceAttribute.LOCATION.getAttributeName()))
    ));
    JsonValue transientState = json(object());

    HiddenValueCallback hiddenValueCallback = new HiddenValueCallback("profile publicKey location");
    hiddenValueCallback.setValue(deviceAttributes.toString());

    ArgumentCaptor<Map<String, Set>> captor = ArgumentCaptor.forClass(Map.class);
    amIdentity.setAttributes(captor.capture());

    // When
    given(amIdentity.isActive()).willReturn(true);
    doThrow(new IdRepoException()).when(amIdentity).store();
    when(amIdentity.getAttribute(anyString())).thenReturn(singleton(deviceAttributes.toString()));
    node.process(getContext(sharedState, transientState, singletonList(hiddenValueCallback)));

  }


  private TreeContext getContext(JsonValue sharedState, JsonValue transientState,
      List<? extends Callback> callbacks) {
    return new TreeContext(sharedState, transientState, new Builder().build(), callbacks);
  }

}