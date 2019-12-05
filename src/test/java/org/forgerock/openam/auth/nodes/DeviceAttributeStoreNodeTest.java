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
import org.json.JSONException;
import org.json.JSONObject;
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
      throws NodeProcessException, IdRepoException, SSOException, JSONException {
    JSONObject deviceAttributes = new JSONObject();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", new JSONObject("{\"test\":\"value\"}"));
    deviceAttributes.put("publicKey", new JSONObject("{\"test\":\"value\"}"));
    deviceAttributes.put("location", new JSONObject("{\"test\":\"value\"}"));

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.PROFILE.getAttributeName())),
        field(DeviceAttribute.PUBLIC_KEY.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.PUBLIC_KEY.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.LOCATION.getAttributeName()))
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
      throws NodeProcessException, IdRepoException, SSOException, JSONException {
    JSONObject deviceAttributes = new JSONObject();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", new JSONObject("{\"test\":\"value\"}"));

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.getString(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.PROFILE.getAttributeName()))
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
      throws NodeProcessException, IdRepoException, SSOException, JSONException {
    JSONObject deviceAttributes = new JSONObject();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", new JSONObject("{\"test\":\"value\"}"));
    deviceAttributes.put("publicKey", new JSONObject("{\"test\":\"value\"}"));
    deviceAttributes.put("location", new JSONObject("{\"test\":\"value\"}"));

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.getString(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.PROFILE.getAttributeName())),
        field(DeviceAttribute.PUBLIC_KEY.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.PUBLIC_KEY.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.LOCATION.getAttributeName()))
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

  @Test(description = "Keep the existing profile and create new profile")
  public void testProcessWithCallbackWithCreate()
      throws NodeProcessException, IdRepoException, SSOException, JSONException {
    JSONObject deviceAttributes = new JSONObject();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", new JSONObject("{\"test\":\"value\"}"));
    deviceAttributes.put("publicKey", new JSONObject("{\"test\":\"value\"}"));
    deviceAttributes.put("location", new JSONObject("{\"test\":\"value\"}"));

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.getString(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.PROFILE.getAttributeName())),
        field(DeviceAttribute.PUBLIC_KEY.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.PUBLIC_KEY.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.LOCATION.getAttributeName()))
    ));
    JsonValue transientState = json(object());

    HiddenValueCallback hiddenValueCallback = new HiddenValueCallback("profile publicKey location");
    hiddenValueCallback.setValue(deviceAttributes.toString());

    JSONObject existingProfile = new JSONObject();
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
    assertThat(captor.getValue().get("deviceAttributes")).contains(deviceAttributes.toString());

  }

  @Test(description = "Existing data is not in json format")
  public void testProcessWithInvalidJsonFormat()
      throws NodeProcessException, IdRepoException, SSOException, JSONException {
    JSONObject deviceAttributes = new JSONObject();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", new JSONObject("{\"test\":\"value\"}"));
    deviceAttributes.put("publicKey", new JSONObject("{\"test\":\"value\"}"));
    deviceAttributes.put("location", new JSONObject("{\"test\":\"value\"}"));

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.getString(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.PROFILE.getAttributeName())),
        field(DeviceAttribute.PUBLIC_KEY.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.PUBLIC_KEY.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.LOCATION.getAttributeName()))
    ));
    JsonValue transientState = json(object());

    HiddenValueCallback hiddenValueCallback = new HiddenValueCallback("profile publicKey location");
    hiddenValueCallback.setValue(deviceAttributes.toString());

    ArgumentCaptor<Map<String, Set>> captor = ArgumentCaptor.forClass(Map.class);
    amIdentity.setAttributes(captor.capture());

    // When
    given(amIdentity.isActive()).willReturn(true);
    //Return the existing one with different identifier
    when(amIdentity.getAttribute(anyString())).thenReturn(singleton("Invalid Json"));
    doNothing().when(amIdentity).setAttributes(captor.capture());
    Action result = node
        .process(getContext(sharedState, transientState, singletonList(hiddenValueCallback)));

    //Then
    assertThat(result.outcome).isEqualTo("outcome");
    assertThat(result.callbacks).isEmpty();
    //Make sure set Attribute contains the profile from the Callback
    assertThat(captor.getValue().get("deviceAttributes")).contains("Invalid Json");
    assertThat(captor.getValue().get("deviceAttributes")).contains(deviceAttributes.toString());

  }

  @Test(description = "SharedState has string instead of JSONObject")
  public void testProcessWithInvalidJsonFormatSharedState()
      throws NodeProcessException, IdRepoException, SSOException, JSONException {
    JSONObject deviceAttributes = new JSONObject();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", "myProfile");

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.getString(DeviceAttribute.IDENTIFIER.getAttributeName())),
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
      throws NodeProcessException, IdRepoException, SSOException, JSONException {
    JSONObject deviceAttributes = new JSONObject();
    deviceAttributes.put("identifier", "testIdentifier");
    deviceAttributes.put("profile", new JSONObject("{\"test\":\"value\"}"));
    deviceAttributes.put("publicKey", new JSONObject("{\"test\":\"value\"}"));
    deviceAttributes.put("location", new JSONObject("{\"test\":\"value\"}"));

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            deviceAttributes.getString(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.PROFILE.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.PROFILE.getAttributeName())),
        field(DeviceAttribute.PUBLIC_KEY.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.PUBLIC_KEY.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            deviceAttributes.getJSONObject(DeviceAttribute.LOCATION.getAttributeName()))
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