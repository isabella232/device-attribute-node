/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2017-2018 ForgeRock AS.
 */


package org.forgerock.openam.auth.nodes;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.idm.IdRepoException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.SingleOutcomeNode;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.CoreWrapper;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.utils.JsonValueBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A node that checks to see if zero-page login headers have specified username and whether that
 * username is in a group permitted to use zero-page login headers.
 */
@Node.Metadata(outcomeProvider = SingleOutcomeNode.OutcomeProvider.class,
    configClass = DeviceAttributeStoreNode.Config.class)
public class DeviceAttributeStoreNode extends SingleOutcomeNode implements DeviceContext {

  public static final String DEVICE_ATTRIBUTES = "deviceAttributes";
  private final Logger logger = LoggerFactory.getLogger(DeviceAttributeStoreNode.class);
  private final CoreWrapper coreWrapper;

  private final Config config;
  private final Realm realm;

  /**
   * Configuration for the node.
   */
  public interface Config {

    @Attribute(order = 100)
    default List<String> deviceAttributes() {
      return Arrays.asList(DeviceAttribute.PROFILE.name(),
          DeviceAttribute.PUBLIC_KEY.name(),
          DeviceAttribute.LOCATION.name());
    }

  }

  /**
   * Create the node using Guice injection. Just-in-time bindings can be used to obtain instances of
   * other classes from the plugin.
   *
   * @param coreWrapper The CoreWrapper
   * @param config The service config.
   * @param realm The realm the node is in.
   */
  @Inject
  public DeviceAttributeStoreNode(CoreWrapper coreWrapper, @Assisted Config config,
      @Assisted Realm realm) {
    this.coreWrapper = coreWrapper;
    this.config = config;
    this.realm = realm;
  }

  @Override
  public Action process(TreeContext context) throws NodeProcessException {
    logger.debug("DeviceAttributeStoreNode started");
    try {
      AMIdentity identity = getUserIdentity(context, coreWrapper, realm);
      save(context, identity);
    } catch (IdRepoException | SSOException e) {
      throw new NodeProcessException(e);
    }

    return goToNext().build();

  }

  /**
   * Persist the attribute to user identity
   *
   * @param identity The user identity
   */
  private void save(TreeContext context, AMIdentity identity)
      throws IdRepoException, SSOException, NodeProcessException {

    final JsonValue[] existing = {JsonValueBuilder.jsonValue().build()};
    String identifier = getIdentifier(context);

    //Remove and overwrite the existing one base on the "identifier"
    Set<String> result = identity.getAttribute(DEVICE_ATTRIBUTES).stream()
        .filter(s -> {
          JsonValue o = JsonValueBuilder.toJsonValue(s);
          if (identifier.equals(o.get(DeviceAttribute.IDENTIFIER.getAttributeName()).asString())) {
            existing[0] = o;
            return false;
          }
          return true;
        }).collect(Collectors.toSet());

    existing[0].put(DeviceAttribute.IDENTIFIER.getAttributeName(), identifier);

    config.deviceAttributes().forEach(deviceAttribute -> {
      try {
        DeviceAttribute da = DeviceAttribute.valueOf(deviceAttribute);
        if (context.sharedState.isDefined(da.getVariableName())) {
          existing[0].put(da.getAttributeName(),
              context.sharedState.get(da.getVariableName()));
        }
      } catch (IllegalArgumentException e) {
        logger.warn(e.getMessage(), e);
      }
    });

    result.add(existing[0].toString());

    //Persist the attribute
    Map<String, Set> attrMap = new HashMap<>();
    attrMap.put(DEVICE_ATTRIBUTES, result);
    identity.setAttributes(attrMap);
    identity.store();

  }

}
