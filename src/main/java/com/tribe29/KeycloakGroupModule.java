package com.tribe29;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.account.GroupBackend;
import com.google.inject.AbstractModule;

public class KeycloakGroupModule extends AbstractModule  {
  @Override
  protected void configure() {
    DynamicSet.bind(binder(), GroupBackend.class).to(KeycloakGroup.class);
  }
}
