package com.intellij.lang.javascript.flex.actions.airpackage;

import com.intellij.lang.javascript.flex.FlexBundle;
import com.intellij.lang.javascript.flex.FlexUtils;
import com.intellij.lang.javascript.flex.actions.AirSigningOptions;
import com.intellij.lang.javascript.flex.actions.FlexBCTree;
import com.intellij.lang.javascript.flex.actions.airmobile.MobileAirUtil;
import com.intellij.lang.javascript.flex.projectStructure.model.FlexIdeBuildConfiguration;
import com.intellij.lang.javascript.flex.projectStructure.model.TargetPlatform;
import com.intellij.lang.javascript.flex.projectStructure.options.BuildConfigurationNature;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;

import static com.intellij.lang.javascript.flex.actions.airpackage.AirPackageParameters.AndroidPackageType;
import static com.intellij.lang.javascript.flex.actions.airpackage.AirPackageParameters.DesktopPackageType;
import static com.intellij.lang.javascript.flex.actions.airpackage.AirPackageParameters.IOSPackageType;

public class AirPackageDialog extends DialogWrapper {

  private JPanel myMainPanel;
  private FlexBCTree myTree;

  private JComboBox myDesktopTypeCombo;

  private JComboBox myAndroidTypeCombo;
  private JCheckBox myApkCaptiveRuntimeCheckBox;
  private JPanel myApkDebugPortPanel;
  private JTextField myApkDebugPortTextField;
  private JPanel myApkDebugHostPanel;
  private JTextField myApkDebugHostTextField;

  private JComboBox myIOSTypeCombo;
  private JCheckBox myIosFastPackagingCheckBox;
  private JLabel myDesktopTypeLabel;
  private JLabel myAndroidTypeLabel;
  private JLabel myIosTypeLabel;

  private final Project myProject;
  private final String myOwnIpAddress;

  protected AirPackageDialog(final Project project) {
    super(project);
    myProject = project;
    myOwnIpAddress = FlexUtils.getOwnIpAddress();

    setTitle(FlexBundle.message("package.air.application.title"));
    setOKButtonText("Package");
    setupComboBoxes();

    init();
    loadParameters();
    updateControlsVisibility();
    updateControlsEnabledState();
  }

  private void setupComboBoxes() {
    myDesktopTypeCombo.setModel(new DefaultComboBoxModel(DesktopPackageType.values()));
    myAndroidTypeCombo.setModel(new DefaultComboBoxModel(AndroidPackageType.values()));
    myIOSTypeCombo.setModel(new DefaultComboBoxModel(IOSPackageType.values()));

    final ActionListener listener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateControlsVisibility();
      }
    };

    myDesktopTypeCombo.addActionListener(listener);
    myAndroidTypeCombo.addActionListener(listener);
    myIOSTypeCombo.addActionListener(listener);
  }

  private void updateControlsVisibility() {
    final AndroidPackageType androidPackaging = (AndroidPackageType)myAndroidTypeCombo.getSelectedItem();
    myApkCaptiveRuntimeCheckBox.setVisible(androidPackaging == AndroidPackageType.Release);
    myApkDebugPortPanel.setVisible(androidPackaging == AndroidPackageType.DebugOverUSB);
    myApkDebugHostPanel.setVisible(androidPackaging == AndroidPackageType.DebugOverNetwork);

    final IOSPackageType iosPackaging = (IOSPackageType)myIOSTypeCombo.getSelectedItem();
    myIosFastPackagingCheckBox.setVisible(iosPackaging == IOSPackageType.DebugOverNetwork || iosPackaging == IOSPackageType.Test);
  }

  private void updateControlsEnabledState() {
    boolean desktopPresent = false;
    boolean androidPresent = false;
    boolean iosPresent = false;

    for (Pair<Module, FlexIdeBuildConfiguration> moduleAndBC : getSelectedBCs()) {
      final FlexIdeBuildConfiguration bc = moduleAndBC.second;
      final BuildConfigurationNature nature = bc.getNature();

      if (nature.isDesktopPlatform()) desktopPresent = true;
      if (nature.isMobilePlatform() && bc.getAndroidPackagingOptions().isEnabled()) androidPresent = true;
      if (nature.isMobilePlatform() && bc.getIosPackagingOptions().isEnabled()) iosPresent = true;
      if (desktopPresent && androidPresent && iosPresent) break;
    }

    myDesktopTypeLabel.setEnabled(desktopPresent);
    myDesktopTypeCombo.setEnabled(desktopPresent);

    myAndroidTypeLabel.setEnabled(androidPresent);
    myAndroidTypeCombo.setEnabled(androidPresent);
    myApkCaptiveRuntimeCheckBox.setEnabled(androidPresent);
    UIUtil.setEnabled(myApkDebugPortPanel, androidPresent, true);
    UIUtil.setEnabled(myApkDebugHostPanel, androidPresent, true);

    myIosTypeLabel.setEnabled(iosPresent);
    myIOSTypeCombo.setEnabled(iosPresent);
    myIosFastPackagingCheckBox.setEnabled(iosPresent);
  }

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  private void createUIComponents() {
    myTree = new FlexBCTree(myProject, new Condition<FlexIdeBuildConfiguration>() {
      public boolean value(final FlexIdeBuildConfiguration bc) {
        final BuildConfigurationNature nature = bc.getNature();
        return nature.isApp() && !nature.isWebPlatform();
      }
    });

    myTree.addToggleCheckBoxListener(new ChangeListener() {
      public void stateChanged(final ChangeEvent e) {
        updateControlsEnabledState();
      }
    });
  }

  protected String getDimensionServiceKey() {
    return "AirPackageDialog.DimensionServiceKey";
  }

  protected String getHelpId() {
    return "reference.flex.package.air.application";
  }

  protected ValidationInfo doValidate() {
    final Collection<Pair<Module, FlexIdeBuildConfiguration>> modulesAndBCs = getSelectedBCs();

    if (modulesAndBCs.isEmpty()) return new ValidationInfo("Please select one or more build configurations");

    if (myApkDebugHostTextField.isVisible() && myApkDebugHostTextField.isEnabled()) {
      try {
        final String portValue = myApkDebugPortTextField.getText().trim();
        final int port = portValue.isEmpty() ? MobileAirUtil.DEBUG_PORT_DEFAULT : Integer.parseInt(portValue);
        if (port <= 0 || port > 65535) return new ValidationInfo("Incorrect port", myApkDebugPortPanel);
      }
      catch (NumberFormatException e) {
        return new ValidationInfo("Incorrect port", myApkDebugPortTextField);
      }
    }

    for (Pair<Module, FlexIdeBuildConfiguration> moduleAndBC : getSelectedBCs()) {
      final FlexIdeBuildConfiguration bc = moduleAndBC.second;

      if (bc.isSkipCompile() && LocalFileSystem.getInstance().findFileByPath(bc.getOutputFilePath(true)) == null) {
        return new ValidationInfo(
          FlexBundle.message("can.not.package.bc", bc.getName(), "compilation is switched off and output *.swf doesn''t exist"));
      }

      final BuildConfigurationNature nature = bc.getNature();
      if (nature.isDesktopPlatform()) {
        if (bc.getAirDesktopPackagingOptions().getPackageFileName().isEmpty()) {
          return new ValidationInfo(FlexBundle.message("can.not.package.bc", bc.getName(), "package file name is not set"));
        }
      }
      else {
        if (!bc.getAndroidPackagingOptions().isEnabled() && !bc.getIosPackagingOptions().isEnabled()) {
          return new ValidationInfo(FlexBundle.message("can.not.package.bc", bc.getName(), "both Android and iOS packaging disabled"));
        }

        if (bc.getAndroidPackagingOptions().isEnabled() && bc.getIosPackagingOptions().isEnabled()) {
          final AndroidPackageType androidPackage = (AndroidPackageType)myAndroidTypeCombo.getSelectedItem();
          final IOSPackageType iosPackage = (IOSPackageType)myIOSTypeCombo.getSelectedItem();
          final boolean androidDebug = androidPackage != AndroidPackageType.Release;
          final boolean iosDebug = iosPackage == IOSPackageType.DebugOverNetwork;

          if (androidDebug != iosDebug) {
            final String message = androidDebug
                                   ? "can''t create debuggable package for Android and non-debuggable package for iOS at once"
                                   : "can''t create debuggable package for iOS and non-debuggable package for Android at once";
            return new ValidationInfo(FlexBundle.message("can.not.package.bc", bc.getName(), message));
          }
        }

        if (bc.getAndroidPackagingOptions().isEnabled()) {
          if (bc.getAndroidPackagingOptions().getPackageFileName().isEmpty()) {
            return new ValidationInfo(FlexBundle.message("can.not.package.bc", bc.getName(), "Android package file name is not set"));
          }
        }

        if (bc.getIosPackagingOptions().isEnabled()) {
          if (bc.getIosPackagingOptions().getPackageFileName().isEmpty()) {
            return new ValidationInfo(FlexBundle.message("can.not.package.bc", bc.getName(), "iOS package file name is not set"));
          }
        }
      }
    }

    return null;
  }

  protected void doOKAction() {
    final Collection<Pair<Module, FlexIdeBuildConfiguration>> selectedBCs = getSelectedBCs();
    if (!checkDisabledCompilation(myProject, selectedBCs)) return;
    if (!checkKeystorePasswords(myProject, selectedBCs)) return;

    saveParameters();
    super.doOKAction();
  }

  private static boolean checkDisabledCompilation(final Project project,
                                                  final Collection<Pair<Module, FlexIdeBuildConfiguration>> selectedBCs) {
    final Collection<FlexIdeBuildConfiguration> bcsWithDisabledCompilation = new ArrayList<FlexIdeBuildConfiguration>();

    for (Pair<Module, FlexIdeBuildConfiguration> moduleAndBC : selectedBCs) {
      if (moduleAndBC.second.isSkipCompile()) {
        bcsWithDisabledCompilation.add(moduleAndBC.second);
      }
    }

    if (!bcsWithDisabledCompilation.isEmpty()) {
      final StringBuilder bcs = new StringBuilder();
      for (FlexIdeBuildConfiguration bc : bcsWithDisabledCompilation) {
        bcs.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>").append(StringUtil.escapeXml(bc.getName())).append("</b><br>");
      }
      final String message = FlexBundle.message("package.bc.with.disabled.compilation", bcsWithDisabledCompilation.size(), bcs.toString());
      final int answer =
        Messages.showYesNoDialog(project, message, FlexBundle.message("package.air.application.title"), Messages.getWarningIcon());

      return answer == Messages.YES;
    }

    return true;
  }

  private static boolean checkKeystorePasswords(final Project project,
                                                final Collection<Pair<Module, FlexIdeBuildConfiguration>> selectedBCs) {
    final Collection<AirSigningOptions> signingOptionsWithUnknownPasswords = new ArrayList<AirSigningOptions>();

    for (Pair<Module, FlexIdeBuildConfiguration> moduleAndBC : selectedBCs) {
      final FlexIdeBuildConfiguration bc = moduleAndBC.second;
      if (bc.getTargetPlatform() == TargetPlatform.Desktop) {
        final AirSigningOptions signingOptions = bc.getAirDesktopPackagingOptions().getSigningOptions();
        if (!signingOptions.isUseTempCertificate() && !PasswordStore.isPasswordKnown(project, signingOptions)) {
          signingOptionsWithUnknownPasswords.add(signingOptions);
        }
      }
      else {
        if (bc.getAndroidPackagingOptions().isEnabled()) {
          final AirSigningOptions signingOptions = bc.getAndroidPackagingOptions().getSigningOptions();
          if (!signingOptions.isUseTempCertificate() && !PasswordStore.isPasswordKnown(project, signingOptions)) {
            signingOptionsWithUnknownPasswords.add(signingOptions);
          }
        }
        if (bc.getIosPackagingOptions().isEnabled()) {
          final AirSigningOptions signingOptions = bc.getIosPackagingOptions().getSigningOptions();
          // signingOptions.isUseTempCertificate() is not applicable for iOS
          if (!PasswordStore.isPasswordKnown(project, signingOptions)) {
            signingOptionsWithUnknownPasswords.add(signingOptions);
          }
        }
      }
    }

    if (!signingOptionsWithUnknownPasswords.isEmpty()) {
      final KeystorePasswordDialog dialog = new KeystorePasswordDialog(project, signingOptionsWithUnknownPasswords);
      dialog.show();
      return dialog.isOK();
    }

    return true;
  }

  private void loadParameters() {
    final AirPackageParameters params = AirPackageParameters.getInstance(myProject);

    myDesktopTypeCombo.setSelectedItem(params.desktopPackageType);

    myAndroidTypeCombo.setSelectedItem(params.androidPackageType);
    myApkCaptiveRuntimeCheckBox.setSelected(params.apkCaptiveRuntime);
    myApkDebugPortTextField.setText(String.valueOf(params.apkDebugListenPort));
    myApkDebugHostTextField.setText(params.apkDebugConnectHost.isEmpty() ? myOwnIpAddress : params.apkDebugConnectHost);

    myIOSTypeCombo.setSelectedItem(params.iosPackageType);
    myIosFastPackagingCheckBox.setSelected(params.iosFastPackaging);
  }

  private void saveParameters() {
    final AirPackageParameters params = AirPackageParameters.getInstance(myProject);

    params.desktopPackageType = (DesktopPackageType)myDesktopTypeCombo.getSelectedItem();

    params.androidPackageType = (AndroidPackageType)myAndroidTypeCombo.getSelectedItem();
    params.apkCaptiveRuntime = myApkCaptiveRuntimeCheckBox.isSelected();

    try {
      final String portValue = myApkDebugPortTextField.getText().trim();
      final int port = portValue.isEmpty() ? MobileAirUtil.DEBUG_PORT_DEFAULT : Integer.parseInt(portValue);
      if (port > 0 && port <= 65535) {
        params.apkDebugListenPort = port;
      }
    }
    catch (NumberFormatException e) {/*ignore*/}

    final String host = myApkDebugHostTextField.getText().trim();
    params.apkDebugConnectHost = host.equals(myOwnIpAddress) ? "" : host;

    params.iosPackageType = (IOSPackageType)myIOSTypeCombo.getSelectedItem();
    params.iosFastPackaging = myIosFastPackagingCheckBox.isSelected();
  }

  public Collection<Pair<Module, FlexIdeBuildConfiguration>> getSelectedBCs() {
    return myTree.getSelectedBCs();
  }

  /*
  protected ValidationInfo doValidate() {
    final String airDescriptorPath = ((String)myAirDescriptorComponent.getComponent().getComboBox().getEditor().getItem()).trim();
    if (airDescriptorPath.length() == 0) {
      return new ValidationInfo("AIR application descriptor path is empty", myAirDescriptorComponent.getComponent());
    }

    final VirtualFile descriptor = LocalFileSystem.getInstance().findFileByPath(airDescriptorPath);
    if (descriptor == null || descriptor.isDirectory()) {
      return new ValidationInfo(FlexBundle.message("file.not.found", airDescriptorPath), myAirDescriptorComponent.getComponent());
    }

    final String installerFileName = myInstallerFileNameComponent.getComponent().getText().trim();
    if (installerFileName.length() == 0) {
      return new ValidationInfo("Package file name is empty", myInstallerFileNameComponent.getComponent());
    }

    final String installerLocation = FileUtil.toSystemDependentName(myInstallerLocationComponent.getComponent().getText().trim());
    if (installerLocation.length() == 0) {
      return new ValidationInfo("Package file location is empty", myInstallerLocationComponent.getComponent());
    }

    final VirtualFile dir = LocalFileSystem.getInstance().findFileByPath(installerLocation);
    if (dir == null || !dir.isDirectory()) {
      return new ValidationInfo(FlexBundle.message("folder.does.not.exist", installerLocation),
                                myInstallerLocationComponent.getComponent());
    }

    if (myFilesToPackageForm.getFilesToPackage().isEmpty()) {
      return new ValidationInfo("No files to package", myFilesToPackageForm.getMainPanel());
    }

    for (AirInstallerParametersBase.FilePathAndPathInPackage path : myFilesToPackageForm.getFilesToPackage()) {
      final String fullPath = FileUtil.toSystemIndependentName(path.FILE_PATH.trim());
      String relPathInPackage = FileUtil.toSystemIndependentName(path.PATH_IN_PACKAGE.trim());
      if (relPathInPackage.startsWith("/")) {
        relPathInPackage = relPathInPackage.substring(1);
      }

      if (fullPath.length() == 0) {
        return new ValidationInfo("Empty file path to package", myFilesToPackageForm.getMainPanel());
      }

      final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(fullPath);
      if (file == null) {
        return new ValidationInfo(FlexBundle.message("file.not.found", fullPath), myFilesToPackageForm.getMainPanel());
      }

      if (relPathInPackage.length() == 0) {
        return new ValidationInfo("Empty relative file path in installation package", myFilesToPackageForm.getMainPanel());
      }

      if (file.isDirectory() && !fullPath.endsWith("/" + relPathInPackage)) {
        return new ValidationInfo(MessageFormat.format("Relative folder path doesn''t match its full path: {0}", relPathInPackage),
                                  myFilesToPackageForm.getMainPanel());
      }
    }

    return mySigningOptionsForm.validate();
  }
  */
}
