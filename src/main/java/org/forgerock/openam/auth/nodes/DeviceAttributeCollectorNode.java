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

import static org.forgerock.openam.auth.node.api.Action.send;
import static org.forgerock.openam.auth.nodes.DeviceAttribute.IDENTIFIER;
import static org.forgerock.openam.auth.nodes.DeviceAttribute.LOCATION;
import static org.forgerock.openam.auth.nodes.DeviceAttribute.PROFILE;
import static org.forgerock.openam.auth.nodes.DeviceAttribute.PUBLIC_KEY;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.authentication.callbacks.HiddenValueCallback;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.apache.commons.lang.StringUtils;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.SingleOutcomeNode;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A node that checks to see if zero-page login headers have specified username and whether that
 * username is in a group permitted to use zero-page login headers.
 */
@Node.Metadata(outcomeProvider = SingleOutcomeNode.OutcomeProvider.class,
    configClass = DeviceAttributeCollectorNode.Config.class)
public class DeviceAttributeCollectorNode extends SingleOutcomeNode {

  private final Logger logger = LoggerFactory.getLogger(DeviceAttributeCollectorNode.class);
  private final Config config;

  /**
   * Configuration for the node.
   */
  public interface Config {

    @Attribute(order = 100)
    default boolean deviceProfile() {
      return true;
    }

    @Attribute(order = 200)
    default boolean devicePublicKey() {
      return false;
    }

    @Attribute(order = 300)
    default boolean deviceLocation() {
      return false;
    }

  }

  /**
   * Create the node using Guice injection. Just-in-time bindings can be used to obtain instances of
   * other classes from the plugin.
   *
   * @param config The service config.
   */
  @Inject
  public DeviceAttributeCollectorNode(@Assisted Config config) {
    this.config = config;
  }

  @Override
  public Action process(TreeContext context) throws NodeProcessException {
    Optional<HiddenValueCallback> opt = context.getCallback(HiddenValueCallback.class);
    if (opt.isPresent() && !StringUtils.isEmpty(opt.get().getValue())) {
      try {
        return save(context, opt.get().getValue());
      } catch (JSONException e) {
        throw new NodeProcessException(e);
      }
    } else {
      return getCallback();
    }
  }

  /**
   * Persist the device attribute to {@link TreeContext#sharedState}
   *
   * @param context The TreeContext
   * @param value A json string which contains keys defined under {@link
   * DeviceAttribute#getAttributeName()}
   * @return Action which updated with {@link TreeContext#sharedState}
   */
  private Action save(TreeContext context, String value) throws JSONException {

    JSONObject source = new JSONObject(value);

    JsonValue newSharedState = context.sharedState.copy();

    newSharedState
        .put(IDENTIFIER.getVariableName(),
            source.get(IDENTIFIER.getAttributeName()));

    if (config.deviceProfile()) {
      newSharedState
          .put(PROFILE.getVariableName(),
              source.has(PROFILE.getAttributeName()) ? source.get(PROFILE.getAttributeName()) : "");
    }
    if (config.devicePublicKey()) {
      newSharedState
          .put(PUBLIC_KEY.getVariableName(),
              source.has(PUBLIC_KEY.getAttributeName()) ? source.get(PUBLIC_KEY.getAttributeName())
                  : "");
    }
    if (config.deviceLocation()) {
      newSharedState
          .put(LOCATION.getVariableName(),
              source.has(LOCATION.getAttributeName()) ? source.get(LOCATION.getAttributeName())
                  : "");
    }
    return goToNext().replaceSharedState(newSharedState).build();


  }

  private Action getCallback() {
    List<String> attributes = new ArrayList<>();
    if (config.deviceProfile()) {
      attributes.add(PROFILE.getAttributeName());
    }
    if (config.devicePublicKey()) {
      attributes.add(PUBLIC_KEY.getAttributeName());
    }
    if (config.deviceLocation()) {
      attributes.add(LOCATION.getAttributeName());
    }

    return send(new HiddenValueCallback(StringUtils.join(attributes, " ")))
        .build();
  }
}
