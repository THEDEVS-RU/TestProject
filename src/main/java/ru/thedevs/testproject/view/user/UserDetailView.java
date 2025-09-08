package ru.thedevs.testproject.view.user;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.Messages;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.action.DialogAction;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.vaadin.flow.component.UI;
import ru.thedevs.entities.Email;
import ru.thedevs.entities.Phone;
import ru.thedevs.entities.UserEntity;
import ru.thedevs.testproject.ContactService;
import ru.thedevs.testproject.UiUtils;
import ru.thedevs.testproject.view.main.MainView;

import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

@Route(value = "user/:id", layout = MainView.class)
@ViewController("User.detail")
@ViewDescriptor("user-detail-view.xml")
@EditedEntityContainer("userDc")
public class UserDetailView<T extends UserEntity> extends StandardDetailView<T> {

    private static final String LOGIN_PAGE = "login";

    @Autowired
    private EntityStates entityStates;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private Dialogs dialogs;
    @Autowired
    private ContactService contactService;
    @Autowired
    private UiUtils uiUtils;
    @Autowired
    private Messages messages;

    @ViewComponent
    private TypedTextField<String> usernameField;
    @ViewComponent
    private PasswordField passwordField;
    @ViewComponent
    private PasswordField confirmPasswordField;
    @ViewComponent
    private ComboBox<String> timeZoneField;
    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private TextField emailField;
    @ViewComponent
    private Button confirmEmailButton;
    @ViewComponent("phoneField")
    private TypedTextField<Long> phoneField;
    @ViewComponent
    private Button removeAccountButton;
    @ViewComponent
    private JmixCheckbox activeField;
    @ViewComponent
    private Button confirmPhoneButton;
    @ViewComponent
    private Button editEmailButton;
    @ViewComponent
    private Button saveEmailButton;
    @ViewComponent
    private Button cancelEmailButton;
    @ViewComponent
    private Button editPhoneButton;
    @ViewComponent
    private Button savePhoneButton;
    @ViewComponent
    private Button cancelPhoneButton;

    @Subscribe
    public void onInit(final InitEvent event) {
        timeZoneField.setItems(List.of(TimeZone.getAvailableIDs()));
    }

    @Subscribe
    public void onInitEntity(final InitEntityEvent<UserEntity> event) {
        usernameField.setReadOnly(false);
        passwordField.setVisible(true);
        confirmPasswordField.setVisible(true);
    }

    @Subscribe
    public void onReady(final ReadyEvent event) {
        if (entityStates.isNew(getEditedEntity())) {
            usernameField.focus();
        }
        emailField.setValue(getEditedEntity().getEmail() != null ? getEditedEntity().getEmail().getEmail() : "");
        phoneField.setTypedValue(getEditedEntity().getPhone() != null ? getEditedEntity().getPhone().getNumber() : null);
        updateButtonsVisibility();
    }

    @Subscribe
    public void onValidation(final ValidationEvent event) {
        if (entityStates.isNew(getEditedEntity())
                && !Objects.equals(passwordField.getValue(), confirmPasswordField.getValue())) {
            event.getErrors().add(messages.getMessage("ru.thedevs.coreui.view.user/passwordsDoNotMatch"));
        }
    }

    @Subscribe
    protected void onBeforeSave(final BeforeSaveEvent event) {
        if (entityStates.isNew(getEditedEntity())) {
            getEditedEntity().setPassword(passwordEncoder.encode(passwordField.getValue()));
        }
    }

    @Subscribe("confirmEmailButton")
    public void onConfirmEmailClick(ClickEvent<Button> event) {
        uiUtils.openConfirmationDialog(
                messages.getMessage("ru.thedevs.coreui.view.user/ConfirmEmail"),
                messages.getMessage("ru.thedevs.coreui.view.user/EnterEmailCodeMessage"),
                code -> {
                    if (contactService.isValidEmailCode(code)) {
                        getEditedEntity().getEmail().setConfirmed(true);
                        updateButtonsVisibility();
                        emailField.setValue(getEditedEntity().getEmail().getEmail());
                    } else {
                        dialogs.createMessageDialog()
                                .withHeader(messages.getMessage("ru.thedevs.coreui.view.user/Error"))
                                .withText(messages.getMessage("ru.thedevs.coreui.view.user/WrongEmailCode"))
                                .open();
                    }
                },
                () -> {
                    if (getEditedEntity().getEmail() != null
                            && !Boolean.TRUE.equals(getEditedEntity().getEmail().getConfirmed())) {
                        getEditedEntity().setEmail(null);
                        emailField.setValue("");
                        updateButtonsVisibility();
                    }
                }
        );
    }

    @Subscribe("editEmailButton")
    public void onEditEmailClick(ClickEvent<Button> event) {
        emailField.setReadOnly(false);
        editEmailButton.setVisible(false);
        saveEmailButton.setVisible(true);
        cancelEmailButton.setVisible(true);
    }

    @Subscribe("saveEmailButton")
    public void onSaveEmailClick(ClickEvent<Button> event) {
        String newEmail = emailField.getValue();
        String currentEmail = getEditedEntity().getEmail() != null
                ? getEditedEntity().getEmail().getEmail()
                : null;

        if (newEmail != null && !newEmail.equals(currentEmail)) {
            Email newEmailEntity = contactService.createNewEmail(newEmail);
            getEditedEntity().setEmail(newEmailEntity);
            updateButtonsVisibility();
        }

        emailField.setReadOnly(true);
        saveEmailButton.setVisible(false);
        cancelEmailButton.setVisible(false);
        editEmailButton.setVisible(true);
    }

    @Subscribe("cancelEmailButton")
    public void onCancelEmailClick(ClickEvent<Button> event) {
        if (getEditedEntity().getEmail() != null) {
            emailField.setValue(getEditedEntity().getEmail().getEmail());
        } else {
            emailField.setValue("");
        }
        emailField.setReadOnly(true);
        saveEmailButton.setVisible(false);
        cancelEmailButton.setVisible(false);
        editEmailButton.setVisible(true);
        updateButtonsVisibility();
    }

    @Subscribe("confirmPhoneButton")
    public void onConfirmPhoneClick(ClickEvent<Button> event) {
        uiUtils.openConfirmationDialog(
                messages.getMessage("ru.thedevs.coreui.view.user/ConfirmPhone"),
                messages.getMessage("ru.thedevs.coreui.view.user/confirmPhoneCaption"),
                code -> {
                    if (contactService.isValidPhoneCode(code)) {
                        getEditedEntity().getPhone().setConfirmed(true);
                        updateButtonsVisibility();
                        phoneField.setTypedValue(getEditedEntity().getPhone().getNumber());
                    } else {
                        dialogs.createMessageDialog()
                                .withHeader(messages.getMessage("ru.thedevs.coreui.view.user/Error"))
                                .withText(messages.getMessage("ru.thedevs.coreui.view.user/WrongPhoneCode"))
                                .open();
                    }
                },
                () -> {
                    if (getEditedEntity().getPhone() != null
                            && !Boolean.TRUE.equals(getEditedEntity().getPhone().getConfirmed())) {
                        getEditedEntity().setPhone(null);
                        phoneField.setTypedValue(null);
                        updateButtonsVisibility();
                    }
                }
        );
    }

    @Subscribe("editPhoneButton")
    public void onEditPhoneClick(ClickEvent<Button> event) {
        phoneField.setReadOnly(false);
        editPhoneButton.setVisible(false);
        savePhoneButton.setVisible(true);
        cancelPhoneButton.setVisible(true);
    }

    @Subscribe("savePhoneButton")
    public void onSavePhoneClick(ClickEvent<Button> event) {
        Long newPhone = phoneField.getTypedValue();
        Long currentPhone = getEditedEntity().getPhone() != null
                ? getEditedEntity().getPhone().getNumber()
                : null;

        if (newPhone != null && !Objects.equals(newPhone, currentPhone)) {
            Phone newPhoneEntity = contactService.createNewPhone(newPhone);
            getEditedEntity().setPhone(newPhoneEntity);
            updateButtonsVisibility();
        }

        phoneField.setReadOnly(true);
        savePhoneButton.setVisible(false);
        cancelPhoneButton.setVisible(false);
        editPhoneButton.setVisible(true);
    }

    @Subscribe("cancelPhoneButton")
    public void onCancelPhoneClick(ClickEvent<Button> event) {
        if (getEditedEntity().getPhone() != null) {
            phoneField.setTypedValue(getEditedEntity().getPhone().getNumber());
        } else {
            phoneField.setTypedValue(null);
        }
        phoneField.setReadOnly(true);
        savePhoneButton.setVisible(false);
        cancelPhoneButton.setVisible(false);
        editPhoneButton.setVisible(true);
        updateButtonsVisibility();
    }

    @Subscribe("removeAccountButton")
    public void onRemoveAccountButtonClick(ClickEvent<Button> event) {
        UserEntity user = getEditedEntity();
        dialogs.createOptionDialog()
                .withHeader(messages.getMessage("ru.thedevs.coreui.view.user/ConfirmAction"))
                .withText(messages.getMessage("ru.thedevs.coreui.view.user/DeleteAccountQuestion"))
                .withActions(
                        new DialogAction(DialogAction.Type.YES).withHandler(e -> {
                            dataManager.remove(user);
                            VaadinSession.getCurrent().getSession().invalidate();
                            VaadinSession.getCurrent().close();
                            UI.getCurrent().getPage().setLocation(LOGIN_PAGE);
                        }),
                        new DialogAction(DialogAction.Type.NO)
                )
                .open();
    }

    private void updateButtonsVisibility() {
        confirmEmailButton.setVisible(
                getEditedEntity().getEmail() != null
                        && !Boolean.TRUE.equals(getEditedEntity().getEmail().getConfirmed())
        );
        confirmPhoneButton.setVisible(
                getEditedEntity().getPhone() != null
                        && !Boolean.TRUE.equals(getEditedEntity().getPhone().getConfirmed())
        );
    }
}
