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

import static org.forgerock.openam.auth.nodes.DeviceAttribute.LOCATION;

import com.google.inject.assistedinject.Assisted;
import com.sun.identity.idm.AMIdentity;
import com.sun.identity.shared.validation.PositiveIntegerValidator;
import com.sun.identity.sm.RequiredValueValidator;
import java.util.Optional;
import javax.inject.Inject;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.CoreWrapper;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.utils.JsonValueBuilder;

/**
 * A node that checks to see if zero-page login headers have specified username and whether that
 * username is in a group permitted to use zero-page login headers.
 */
@Node.Metadata(outcomeProvider = AbstractDecisionNode.OutcomeProvider.class,
    configClass = DeviceLocationRangeNode.Config.class)
public class DeviceLocationRangeNode extends AbstractDecisionNode implements DeviceContext {

  public static final String DEVICE_ATTRIBUTES = "deviceAttributes";
  public static final String LATITUDE = "latitude";
  public static final String LONGITUDE = "longitude";
  private final CoreWrapper coreWrapper;
  private final Config config;
  private final Realm realm;

  /**
   * Configuration for the node.
   */
  public interface Config {

    @Attribute(order = 100, validators = {RequiredValueValidator.class,
        PositiveIntegerValidator.class})
    default String distance() {
      return "100";
    }

  }

  /**
   * Create the node using Guice injection. Just-in-time bindings can be used to obtain instances of
   * other classes from the plugin.
   *
   * @param config The service config.
   */
  @Inject
  public DeviceLocationRangeNode(
      CoreWrapper coreWrapper, @Assisted Config config,
      @Assisted Realm realm) {
    this.coreWrapper = coreWrapper;
    this.config = config;
    this.realm = realm;
  }

  @Override
  public Action process(TreeContext context) throws NodeProcessException {

    JsonValue location = getLocation(context);
    String identifier = getIdentifier(context);

    try {
      AMIdentity identity = getUserIdentity(context, coreWrapper, realm);
      Optional<String> result = identity.getAttribute(DEVICE_ATTRIBUTES).stream()
          //Find matching device with same identifier and has location
          .filter(s -> {
            JsonValue o = JsonValueBuilder.toJsonValue(s);
            if (identifier
                .equals(o.get(DeviceAttribute.IDENTIFIER.getAttributeName()).asString())) {
              JsonValue loc = o.get(LOCATION.getAttributeName());
              return loc.isDefined(LONGITUDE) && loc.isDefined(LATITUDE);
            }
            return false;
          })
          .findFirst();

      if (result.isPresent()) {
        JsonValue deviceAttribute = JsonValueBuilder.toJsonValue(result.get());
        JsonValue storedLocation = deviceAttribute.get(LOCATION.getAttributeName());
        if (distance(location.get(LATITUDE).asDouble(),
            location.get(LONGITUDE).asDouble(),
            storedLocation.get(LATITUDE).asDouble(),
            storedLocation.get(LONGITUDE).asDouble())
            < Double.parseDouble(config.distance())) {
          return goTo(true).build();
        }
      }

      return goTo(false).build(); //When context does not exist or not within distance

    } catch (NodeProcessException e) {
      throw e;
    } catch (Exception e) {
      throw new NodeProcessException(e);
    }
  }

  private double distance(double fromLat, double fromLon, double toLat, double toLon) {
    if ((fromLat == toLat) && (fromLon == toLon)) {
      return 0;
    } else {
      double diffLon = fromLon - toLon;
      double dist = Math.sin(Math.toRadians(fromLat)) * Math.sin(Math.toRadians(toLat))
          + Math.cos(Math.toRadians(fromLat)) * Math.cos(Math.toRadians(toLat)) * Math
          .cos(Math.toRadians(diffLon));
      dist = Math.acos(dist);
      dist = Math.toDegrees(dist);
      dist = dist * 60 * 1.1515;
      return dist * 1.609344;
    }
  }


}
