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
import org.forgerock.openam.auth.nodes.DeviceLocationRangeNode.Config;
import org.forgerock.openam.core.CoreWrapper;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.utils.JsonValueBuilder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DeviceLocationRangeNodeTest {

  @Mock
  CoreWrapper coreWrapper;

  @Mock
  AMIdentity amIdentity;

  @Mock
  Realm realm;

  @Mock
  Config config;

  @InjectMocks
  DeviceLocationRangeNode node;

  @BeforeMethod
  public void setup() throws IdRepoException, SSOException {
    node = null;
    initMocks(this);
    given(realm.asDN()).willReturn("/");
    given(coreWrapper.getIdentity(anyString(), anyString())).willReturn(amIdentity);
    given(amIdentity.isExists()).willReturn(true);
    given(amIdentity.isActive()).willReturn(true);
    given(config.distance()).willReturn("100");
  }

  @Test
  public void testProcessContextWithinRange()
      throws NodeProcessException, IdRepoException, SSOException {

    JsonValue storedLocation = JsonValueBuilder.jsonValue().build();
    storedLocation.put("longitude", 49.164532);
    storedLocation.put("latitude", -123.177201);

    JsonValue newLocation = JsonValueBuilder.jsonValue().build();
    newLocation.put("longitude", 49.164553);
    newLocation.put("latitude", -123.175012);

    JsonValue existing = JsonValueBuilder.jsonValue().build();
    existing.put("identifier", "testIdentifier");
    existing.put("location", storedLocation);

    JsonValue collectedAttributes = JsonValueBuilder.jsonValue().build();
    collectedAttributes.put("identifier", "testIdentifier");
    collectedAttributes.put("location", newLocation);

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            collectedAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            collectedAttributes.get(DeviceAttribute.LOCATION.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    when(amIdentity.getAttribute(anyString()))
        .thenReturn(Collections.singleton(existing.toString()));
    Action result = node.process(getContext(sharedState, transientState, emptyList()));

    //Then
    assertThat(result.outcome).isEqualTo("true");
  }

  @Test
  public void testProcessContextWithExactMatch()
      throws NodeProcessException, IdRepoException, SSOException {

    JsonValue storedLocation = JsonValueBuilder.jsonValue().build();
    storedLocation.put("longitude", 49.164532);
    storedLocation.put("latitude", -123.177201);

    JsonValue newLocation = JsonValueBuilder.jsonValue().build();
    newLocation.put("longitude", 49.164532);
    newLocation.put("latitude", -123.177201);

    JsonValue existing = JsonValueBuilder.jsonValue().build();
    existing.put("identifier", "testIdentifier");
    existing.put("location", storedLocation);

    JsonValue collectedAttributes = JsonValueBuilder.jsonValue().build();
    collectedAttributes.put("identifier", "testIdentifier");
    collectedAttributes.put("location", newLocation);

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            collectedAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            collectedAttributes.get(DeviceAttribute.LOCATION.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    when(amIdentity.getAttribute(anyString()))
        .thenReturn(Collections.singleton(existing.toString()));
    Action result = node.process(getContext(sharedState, transientState, emptyList()));

    //Then
    assertThat(result.outcome).isEqualTo("true");
  }

  @Test
  public void testProcessContextWithOutOfRange()
      throws NodeProcessException, IdRepoException, SSOException {

    //Vancouver
    JsonValue storedLocation = JsonValueBuilder.jsonValue().build();
    storedLocation.put("longitude", 49.164532);
    storedLocation.put("latitude", -123.177201);

    //Winnipeg
    JsonValue newLocation = JsonValueBuilder.jsonValue().build();
    newLocation.put("longitude", 49.878418);
    newLocation.put("latitude", -97.130854);

    JsonValue existing = JsonValueBuilder.jsonValue().build();
    existing.put("identifier", "testIdentifier");
    existing.put("location", storedLocation);

    JsonValue collectedAttributes = JsonValueBuilder.jsonValue().build();
    collectedAttributes.put("identifier", "testIdentifier");
    collectedAttributes.put("location", newLocation);

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            collectedAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            collectedAttributes.get(DeviceAttribute.LOCATION.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    when(amIdentity.getAttribute(anyString()))
        .thenReturn(Collections.singleton(existing.toString()));
    Action result = node.process(getContext(sharedState, transientState, emptyList()));

    //Then
    assertThat(result.outcome).isEqualTo("false");
  }

  @Test
  public void testProcessDeviceNotFound()
      throws NodeProcessException, IdRepoException, SSOException {

    //Vancouver
    JsonValue storedLocation = JsonValueBuilder.jsonValue().build();
    storedLocation.put("longitude", 49.164532);
    storedLocation.put("latitude", -123.177201);

    //Winnipeg
    JsonValue newLocation = JsonValueBuilder.jsonValue().build();
    newLocation.put("longitude", 49.164532);
    newLocation.put("latitude", -123.177201);

    JsonValue existing = JsonValueBuilder.jsonValue().build();
    existing.put("identifier", "testIdentifier1");
    existing.put("location", storedLocation);

    JsonValue collectedAttributes = JsonValueBuilder.jsonValue().build();
    collectedAttributes.put("identifier", "testIdentifier2");
    collectedAttributes.put("location", newLocation);

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            collectedAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            collectedAttributes.get(DeviceAttribute.LOCATION.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    when(amIdentity.getAttribute(anyString()))
        .thenReturn(Collections.singleton(existing.toString()));
    Action result = node.process(getContext(sharedState, transientState, emptyList()));

    //Then
    assertThat(result.outcome).isEqualTo("false");
  }

  @Test
  public void testProcessLocationNotFound()
      throws NodeProcessException, IdRepoException, SSOException {

    //Winnipeg
    JsonValue newLocation = JsonValueBuilder.jsonValue().build();
    newLocation.put("longitude", 49.164532);
    newLocation.put("latitude", -123.177201);

    JsonValue existing = JsonValueBuilder.jsonValue().build();
    existing.put("identifier", "testIdentifier1");

    JsonValue collectedAttributes = JsonValueBuilder.jsonValue().build();
    collectedAttributes.put("identifier", "testIdentifier2");
    collectedAttributes.put("location", newLocation);

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            collectedAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            collectedAttributes.get(DeviceAttribute.LOCATION.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    when(amIdentity.getAttribute(anyString()))
        .thenReturn(Collections.singleton(existing.toString()));
    Action result = node.process(getContext(sharedState, transientState, emptyList()));

    //Then
    assertThat(result.outcome).isEqualTo("false");
  }

  @Test(expectedExceptions = NodeProcessException.class, expectedExceptionsMessageRegExp = "Device Attribute Collector Node to collect device location is required")
  public void testProcessLocationNotInContext()
      throws NodeProcessException {

    JsonValue collectedAttributes = JsonValueBuilder.jsonValue().build();
    collectedAttributes.put("identifier", "testIdentifier");

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            collectedAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    node.process(getContext(sharedState, transientState, emptyList()));

  }

  @Test(expectedExceptions = NodeProcessException.class, expectedExceptionsMessageRegExp = "Could not get a valid username from the context")
  public void testProcessContextNoUserInContext()
      throws NodeProcessException {

    JsonValue newLocation = JsonValueBuilder.jsonValue().build();
    newLocation.put("longitude", 49.164532);
    newLocation.put("latitude", -123.177201);

    JsonValue collectedAttributes = JsonValueBuilder.jsonValue().build();
    collectedAttributes.put("identifier", "testIdentifier2");
    collectedAttributes.put("location", newLocation);

    JsonValue sharedState = json(object(
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            collectedAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            collectedAttributes.get(DeviceAttribute.LOCATION.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    node.process(getContext(sharedState, transientState, emptyList()));

  }

  @Test
  public void testProcessStoreAttributeInvalidJson()
      throws NodeProcessException, IdRepoException, SSOException {

    //Winnipeg
    JsonValue newLocation = JsonValueBuilder.jsonValue().build();
    newLocation.put("longitude", 49.878418);
    newLocation.put("latitude", -97.130854);

    JsonValue existing = JsonValueBuilder.jsonValue().build();
    existing.put("identifier", "testIdentifier");
    existing.put("location", "invalid");

    JsonValue collectedAttributes = JsonValueBuilder.jsonValue().build();
    collectedAttributes.put("identifier", "testIdentifier");
    collectedAttributes.put("location", newLocation);

    JsonValue sharedState = json(object(field(USERNAME, "bob"),
        field(DeviceAttribute.IDENTIFIER.getVariableName(),
            collectedAttributes.get(DeviceAttribute.IDENTIFIER.getAttributeName())),
        field(DeviceAttribute.LOCATION.getVariableName(),
            collectedAttributes.get(DeviceAttribute.LOCATION.getAttributeName()))
    ));

    JsonValue transientState = json(object());

    // When
    when(amIdentity.getAttribute(anyString()))
        .thenReturn(Collections.singleton(existing.toString()));
    Action result = node.process(getContext(sharedState, transientState, emptyList()));

    //Then
    assertThat(result.outcome).isEqualTo("false");
  }


  private TreeContext getContext(JsonValue sharedState, JsonValue transientState,
      List<? extends Callback> callbacks) {
    return new TreeContext(sharedState, transientState, new Builder().build(), callbacks);
  }

}