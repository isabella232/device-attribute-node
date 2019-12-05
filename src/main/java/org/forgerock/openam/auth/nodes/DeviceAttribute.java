package org.forgerock.openam.auth.nodes;

public enum DeviceAttribute {
  PROFILE("forgeRock.mobile.profile", "profile"),
  PUBLIC_KEY("forgeRock.mobile.publicKey", "publicKey"),
  LOCATION("forgeRock.mobile.location", "location"),
  IDENTIFIER("forgeRock.mobile.identifier", "identifier");

  /**
   * Device Attribute definition
   *
   * @param variableName The variable name stored under the {@link org.forgerock.openam.auth.node.api.TreeContext}
   * @param attributeName The key name stored under the data store.
   */
  DeviceAttribute(String variableName, String attributeName) {
    this.variableName = variableName;
    this.attributeName = attributeName;
  }

  private String variableName;
  private String attributeName;

  public String getVariableName() {
    return variableName;
  }

  public String getAttributeName() {
    return attributeName;
  }
}
