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
import com.sun.identity.sm.RequiredValueValidator;
import javax.inject.Inject;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;

/**
 * A node that checks to see if zero-page login headers have specified username and whether that
 * username is in a group permitted to use zero-page login headers.
 */
@Node.Metadata(outcomeProvider = AbstractDecisionNode.OutcomeProvider.class,
    configClass = DeviceJailBreakVerificationNode.Config.class)
public class DeviceJailBreakVerificationNode extends AbstractDecisionNode implements DeviceContext {

  private final Config config;

  /**
   * Configuration for the node.
   */
  public interface Config {

    @Attribute(order = 100, validators = RequiredValueValidator.class)
    default String score() {
      return "0";
    }
  }

  /**
   * Create the node using Guice injection. Just-in-time bindings can be used to obtain instances of
   * other classes from the plugin.
   *
   * @param config The service config.
   */
  @Inject
  public DeviceJailBreakVerificationNode(@Assisted Config config) {
    this.config = config;
  }

  @Override
  public Action process(TreeContext context) throws NodeProcessException {

    JsonValue profile = getProfile(context);
    if (profile.get("platform").get("jailBreakScore").asDouble() > Double
        .parseDouble(config.score())) {
      return goTo(false).build();
    }
    return goTo(true).build();

  }
}
