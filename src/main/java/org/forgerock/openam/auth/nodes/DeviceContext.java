package org.forgerock.openam.auth.nodes;

import static org.forgerock.openam.auth.node.api.SharedStateConstants.USERNAME;
import static org.forgerock.openam.auth.nodes.DeviceAttribute.LOCATION;
import static org.forgerock.openam.auth.nodes.DeviceAttribute.PROFILE;

import com.iplanet.sso.SSOException;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdRepoException;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.CoreWrapper;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.utils.StringUtils;

public interface DeviceContext {

  default JsonValue getProfile(TreeContext context) throws NodeProcessException {
    if (context.sharedState.isDefined(PROFILE.getVariableName())) {
      return context.sharedState.get(PROFILE.getVariableName());
    } else {
      throw new NodeProcessException(
          "Device Attribute Collector Node to collect device profile is required");
    }
  }

  default JsonValue getLocation(TreeContext context) throws NodeProcessException {
    if (context.sharedState.isDefined(LOCATION.getVariableName())) {
      return context.sharedState.get(LOCATION.getVariableName());
    } else {
      throw new NodeProcessException(
          "Device Attribute Collector Node to collect device location is required");
    }
  }


  default String getIdentifier(TreeContext context) throws NodeProcessException {
    String identifier = context.sharedState.get(DeviceAttribute.IDENTIFIER.getVariableName())
        .asString();
    if (StringUtils.isBlank(identifier)) {
      throw new NodeProcessException("Device Identifier cannot be found from the Context");
    }
    return identifier;
  }

  default AMIdentity getUserIdentity(TreeContext context, CoreWrapper coreWrapper, Realm realm)
      throws NodeProcessException, IdRepoException, SSOException {
    String username = context.sharedState.get(USERNAME).asString();
    if (username == null || username.isEmpty()) {
      throw new NodeProcessException("Could not get a valid username from the context");
    }

    AMIdentity userIdentity = coreWrapper.getIdentity(username, realm.asDN());
    if (userIdentity == null || !userIdentity.isExists() || !userIdentity.isActive()) {
      throw new NodeProcessException("User does not exist or inactive");
    }
    return userIdentity;
  }

}
