package com.jetbrains.edu.learning.checkio.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.HoverHyperlinkLabel;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.edu.learning.checkio.controllers.CheckioAuthorizationController;
import com.jetbrains.edu.learning.checkio.model.CheckioUser;
import com.jetbrains.edu.learning.checkio.ui.CheckioOptionsUIProvider;
import com.jetbrains.edu.learning.settings.OptionsProvider;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.util.Objects;

public class CheckioOptions implements OptionsProvider {
  private final CheckioOptionsUIProvider UIProvider = new CheckioOptionsUIProvider();

  private JBLabel myLoginLabel = UIProvider.getLoginLabel();
  private HoverHyperlinkLabel myLoginLink = UIProvider.getLoginLink();
  private JPanel myPanel = UIProvider.getPanel();
  private HyperlinkAdapter myLoginListener;

  private CheckioUser myUser;

  @Nls
  @Override
  public String getDisplayName() {
    return "CheckiO options";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public boolean isModified() {
    return !Objects.equals(myUser, CheckioSettings.getInstance().getUser());
  }

  @Override
  public void reset() {
    myUser = CheckioSettings.getInstance().getUser();
    updateLoginLabels(myUser);
  }

  @Override
  public void apply() throws ConfigurationException {
    if (isModified()) {
      CheckioSettings.getInstance().setUser(myUser);
    }

    reset();
  }

  private void updateLoginLabels(CheckioUser user) {
    if (myLoginListener != null) {
      myLoginLink.removeHyperlinkListener(myLoginListener);
    }

    if (user == null) {
      myLoginLabel.setText("You're not logged in");
      myLoginLink.setText("Log in to CheckiO");

      myLoginListener = createAuthorizeListener();
    } else {
      myLoginLabel.setText("You're logged in as " + user.getUsername());
      myLoginLink.setText("Log out");

      myLoginListener = createLogoutListener();
    }

    myLoginLink.addHyperlinkListener(myLoginListener);
  }

  private HyperlinkAdapter createAuthorizeListener() {
    return new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent event) {
        ApplicationManager.getApplication().getMessageBus().connect().subscribe(CheckioAuthorizationController.LOGGED_IN, (newUser) -> {
          if (!Objects.equals(myUser, newUser)) {
            CheckioSettings.getInstance().setUser(myUser);
            myUser = newUser;
            updateLoginLabels(myUser);
          }
        });
        CheckioAuthorizationController.doAuthorize();
      }
    };
  }

  private HyperlinkAdapter createLogoutListener() {
    return new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent event) {
        myUser = null;
        updateLoginLabels(null);
      }
    };
  }
}