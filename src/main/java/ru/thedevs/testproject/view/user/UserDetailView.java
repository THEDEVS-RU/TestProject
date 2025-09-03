package ru.thedevs.testproject.view.user;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import io.jmix.core.DataManager;
import io.jmix.flowui.Dialogs;
import io.jmix.flowui.action.DialogAction;
import ru.thedevs.entities.Email;
import ru.thedevs.entities.Phone;
import ru.thedevs.entities.Url;
import ru.thedevs.entities.UserEntity;
import io.jmix.core.EntityStates;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.vaadin.flow.component.UI;
import ru.thedevs.testproject.view.main.MainView;

import java.util.*;
import java.util.stream.Collectors;

@Route(value = "User/:id", layout = MainView.class)
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
    @ViewComponent
    private TypedTextField<String> emailCodeField;
    @ViewComponent
    private TypedTextField<String> phoneCodeField;
    @ViewComponent
    private TextField contactLinkField;

    /**
     * Инициализация.
     * Устанавливает список доступных временных зон и подставляет значения email и телефона из сущности.
     *
     * @param event событие инициализации
     */
    @Subscribe
    public void onInit(final InitEvent event) {
        timeZoneField.setItems(List.of(TimeZone.getAvailableIDs()));


    }

    /**
     * Вызывается при создании новой сущности.
     * Делает поля логина и пароля доступными для ввода.
     *
     * @param event событие инициализации сущности
     */
    @Subscribe
    public void onInitEntity(final InitEntityEvent<UserEntity> event) {
        usernameField.setReadOnly(false);
        passwordField.setVisible(true);
        confirmPasswordField.setVisible(true);
    }

    /**
     * Вызывается после загрузки данных в форму.
     * При новом пользователе ставит фокус на логин,
     * заполняет email/телефон, управляет видимостью кнопок подтверждения
     * и восстанавливает ссылки из связанных сущностей.
     *
     * @param event событие готовности
     */
    @Subscribe
    public void onReady(final ReadyEvent event) {
        if (entityStates.isNew(getEditedEntity())) {
            usernameField.focus();
        }

        if (getEditedEntity().getEmail() != null) {
            emailField.setValue(getEditedEntity().getEmail().getEmail());

            boolean confirmed = Boolean.TRUE.equals(getEditedEntity().getEmail().getConfirmed());
            confirmEmailButton.setVisible(!confirmed);
            emailCodeField.setVisible(!confirmed);
        } else {
            confirmEmailButton.setVisible(false);
            emailCodeField.setVisible(false);
        }

        if (getEditedEntity().getPhone() != null) {
            phoneField.setTypedValue(getEditedEntity().getPhone().getNumber());

            boolean confirmed = Boolean.TRUE.equals(getEditedEntity().getPhone().getConfirmed());
            confirmPhoneButton.setVisible(!confirmed);
            phoneCodeField.setVisible(!confirmed);
        } else {
            confirmPhoneButton.setVisible(false);
            phoneCodeField.setVisible(false);
        }

        Set<Url> urls = getEditedEntity().getUrls();
        if (urls != null && !urls.isEmpty()) {
            String joined = urls.stream()
                    .map(Url::getUrl) //
                    .collect(Collectors.joining(", "));
            contactLinkField.setValue(joined);
        }
    }

    /**
     * Проверка валидности введённых данных.
     * Убедиться, что пароли совпадают при создании нового пользователя.
     *
     * @param event событие валидации
     */
    @Subscribe
    public void onValidation(final ValidationEvent event) {
        if (entityStates.isNew(getEditedEntity())
                && !Objects.equals(passwordField.getValue(), confirmPasswordField.getValue())) {
            event.getErrors().add(messageBundle.getMessage("passwordsDoNotMatch"));
        }
    }

    /**
     * Выполняется перед сохранением пользователя.
     * Шифрует новый пароль, обновляет email и телефон при изменении,
     * очищает старые записи и создаёт новые сущности,
     * а также сохраняет ссылки из поля контактов.
     *
     * @param event событие перед сохранением
     */
    @Subscribe
    protected void onBeforeSave(final BeforeSaveEvent event) {
        if (entityStates.isNew(getEditedEntity())) {
            getEditedEntity().setPassword(passwordEncoder.encode(passwordField.getValue()));
        }

        String newEmail = emailField.getValue();
        if (newEmail != null) {
            Email currentEmail = getEditedEntity().getEmail();
            if (currentEmail == null || !Objects.equals(currentEmail.getEmail(), newEmail)) {
                if (currentEmail != null) {
                    dataManager.remove(currentEmail);
                }

                Email newEmailEntity = dataManager.create(Email.class);
                newEmailEntity.setEmail(newEmail);
                newEmailEntity.setConfirmed(false);
                dataManager.save(newEmailEntity);
                getEditedEntity().setEmail(newEmailEntity);
            }
        }

        Long newPhone = phoneField.getTypedValue();
        if (newPhone != null) {
            Phone currentPhone = getEditedEntity().getPhone();
            if (currentPhone == null || !Objects.equals(currentPhone.getNumber(), newPhone)) {
                if (currentPhone != null) {
                    dataManager.remove(currentPhone);
                }

                Phone newPhoneEntity = dataManager.create(Phone.class);
                newPhoneEntity.setNumber(newPhone);
                dataManager.save(newPhoneEntity);
                getEditedEntity().setPhone(newPhoneEntity);
            }
        }

        String linksValue = contactLinkField.getValue();
        Set<Url> existingUrls = new HashSet<>(getEditedEntity().getUrls());

        if (linksValue != null && !linksValue.isBlank()) {
            Set<String> newUrls = Arrays.stream(linksValue.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());

            existingUrls.stream()
                    .filter(url -> !newUrls.contains(url.getUrl()))
                    .forEach(url -> {
                        getEditedEntity().getUrls().remove(url);
                        dataManager.remove(url);
                    });

            newUrls.stream()
                    .filter(newUrl -> existingUrls.stream().noneMatch(u -> u.getUrl().equals(newUrl)))
                    .forEach(newUrl -> {
                        Url url = dataManager.create(Url.class);
                        url.setUrl(newUrl);
                        url.setUser(getEditedEntity());
                        getEditedEntity().getUrls().add(url);
                    });

        } else {
            existingUrls.forEach(url -> {
                getEditedEntity().getUrls().remove(url);
                dataManager.remove(url);
            });
        }

    }

    /**
     * Обработчик нажатия кнопки подтвердения email'a".
     * Устанавливает флаг подтверждения email и сохраняет изменения.
     *
     * @param event событие клика
     */
    @Subscribe("confirmEmailButton")
    public void onConfirmEmailBtnClick(ClickEvent<Button> event) {
        if (getEditedEntity().getEmail() != null) {
            String code = emailCodeField.getValue();

            if (isValidEmailCode(code)) {
                getEditedEntity().getEmail().setConfirmed(true);
                dataManager.save(getEditedEntity().getEmail());

                emailCodeField.clear();
                emailCodeField.setVisible(false);
                confirmEmailButton.setVisible(false);
            } else {
                dialogs.createMessageDialog()
                        .withHeader("Ошибка")
                        .withText("Неверный код подтверждения e-mail")
                        .open();
            }
        }
    }

    /**
     * Обработчик нажатия кнопки Изменить для email'a.
     * Делает поле email'a доступным для редактирования и показывает кнопки Сохранить/Отменить.
     *
     * @param event событие клика
     */
    @Subscribe("editEmailButton")
    public void onEditEmailClick(ClickEvent<Button> event) {
        emailField.setReadOnly(false);
        editEmailButton.setVisible(false);
        saveEmailButton.setVisible(true);
        cancelEmailButton.setVisible(true);
    }

    /**
     * Обработчик нажатия кнопки Сохранить для email'a.
     * Блокирует редактирование поля email, обновляет сущность при изменении значения,
     * скрывает кнопки Сохранить/Отменить, показывает кнопку редактирования
     * и при необходимости отображает элементы подтверждения email'a.
     *
     * @param event событие клика
     */
    @Subscribe("saveEmailButton")
    public void onSaveEmailClick(ClickEvent<Button> event) {
        emailField.setReadOnly(true);
        saveEmailButton.setVisible(false);
        cancelEmailButton.setVisible(false);
        editEmailButton.setVisible(true);

        String newEmail = emailField.getValue();
        if (newEmail != null) {
            Email currentEmail = getEditedEntity().getEmail();
            if (currentEmail == null || !Objects.equals(currentEmail.getEmail(), newEmail)) {
                Email newEmailEntity = dataManager.create(Email.class);
                newEmailEntity.setEmail(newEmail);
                newEmailEntity.setConfirmed(false);
                getEditedEntity().setEmail(newEmailEntity);
            }
        }

        if (getEditedEntity().getEmail() != null
                && Boolean.FALSE.equals(getEditedEntity().getEmail().getConfirmed())) {
            emailCodeField.setVisible(true);
            confirmEmailButton.setVisible(true);
        } else {
            emailCodeField.setVisible(false);
            confirmEmailButton.setVisible(false);
        }
    }

    /**
     * Обработчик нажатия кнопки «Отменить» для email.
     * Возвращает исходное значение email, делает поле недоступным для редактирования
     * и скрывает кнопки кнопки Сохранить/Отменить.
     *
     * @param event событие клика
     */
    @Subscribe("cancelEmailButton")
    public void onCancelEmailClick(ClickEvent<Button> event) {
        if (getEditedEntity().getEmail() != null) {
            emailField.setValue(getEditedEntity().getEmail().getEmail());
        }
        emailField.setReadOnly(true);
        saveEmailButton.setVisible(false);
        cancelEmailButton.setVisible(false);
        editEmailButton.setVisible(true);
    }

    /**
     * Обработчик нажатия кнопки подтвердения номера телефона.
     * Устанавливает флаг подтверждения номера телефона и сохраняет изменения.
     *
     * @param event событие клика
     */

    @Subscribe("confirmPhoneButton")
    public void onConfirmPhoneBtnClick(ClickEvent<Button> event) {
        if (getEditedEntity().getPhone() != null) {
            String code = phoneCodeField.getValue();

            if (isValidPhoneCode(code)) {
                getEditedEntity().getPhone().setConfirmed(true);
                dataManager.save(getEditedEntity().getPhone());

                phoneCodeField.clear();
                phoneCodeField.setVisible(false);
                confirmPhoneButton.setVisible(false);
            } else {
                dialogs.createMessageDialog()
                        .withHeader("Ошибка")
                        .withText("Неверный код подтверждения телефона")
                        .open();
            }
        }
    }


    /**
     * Обработчик нажатия кнопки Изменить для телефона.
     * Делает поле телефона доступным для редактирования и показывает кнопки Сохранить/Отменить.
     *
     * @param event событие клика
     */
    @Subscribe("editPhoneButton")
    public void onEditPhoneClick(ClickEvent<Button> event) {
        phoneField.setReadOnly(false);
        editPhoneButton.setVisible(false);
        savePhoneButton.setVisible(true);
        cancelPhoneButton.setVisible(true);
    }

    /**
     * Обработчик нажатия кнопки Сохранить для телефона.
     * Делает поле телефона недоступным для редактирования, скрывает кнопки Сохранить/Отменить.
     * и показывает кнопку подтверждения номера телефона.
     *
     * @param event событие клика
     */
    @Subscribe("savePhoneButton")
    public void onSavePhoneClick(ClickEvent<Button> event) {
        phoneField.setReadOnly(true);
        savePhoneButton.setVisible(false);
        cancelPhoneButton.setVisible(false);
        editPhoneButton.setVisible(true);

        Long newPhone = phoneField.getTypedValue();
        if (newPhone != null) {
            Phone currentPhone = getEditedEntity().getPhone();
            if (currentPhone == null || !Objects.equals(currentPhone.getNumber(), newPhone)) {
                Phone newPhoneEntity = dataManager.create(Phone.class);
                newPhoneEntity.setNumber(newPhone);
                newPhoneEntity.setConfirmed(false);
                getEditedEntity().setPhone(newPhoneEntity);
            }
        }

        if (getEditedEntity().getPhone() != null
                && Boolean.FALSE.equals(getEditedEntity().getPhone().getConfirmed())) {
            phoneCodeField.setVisible(true);
            confirmPhoneButton.setVisible(true);
        } else {
            phoneCodeField.setVisible(false);
            confirmPhoneButton.setVisible(false);
        }
    }


    /**
     * Обработчик нажатия кнопки Отменить для телефона.
     * Возвращает исходное значение телефона, делает поле недоступным для редактирования
     * и скрывает кнопки Сохранить/Отменить.
     *
     * @param event событие клика
     */
    @Subscribe("cancelPhoneButton")
    public void onCancelPhoneClick(ClickEvent<Button> event) {
        if (getEditedEntity().getPhone() != null) {
            phoneField.setValue(String.valueOf(getEditedEntity().getPhone().getNumber()));
        }
        phoneField.setReadOnly(true);
        savePhoneButton.setVisible(false);
        cancelPhoneButton.setVisible(false);
        editPhoneButton.setVisible(true);
    }

    /**
     * Обработчик нажатия кнопки удаления аккаунта.
     * Показывает диалог подтверждения, удаляет пользователя, завершает сессию
     * и перенаправляет на страницу логина.
     *
     * @param event событие клика
     */
    @Subscribe("removeAccountButton")
    public void onRemoveAccountButtonClick(ClickEvent<Button> event) {
        UserEntity user = getEditedEntity();
        dialogs.createOptionDialog()
                .withHeader("confirmAction")
                .withText("deleteAccountQuestion")
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

    /**
     * Проверяет введённый код подтверждения email.
     * Сейчас используется заглушка, ожидается код "1234".
     *
     * @param code введённый пользователем код
     * @return true, если код корректный
     */
    private boolean isValidEmailCode(String code) {
        // TODO: заменить на настоящую проверку
        return "1234".equals(code);
    }

    /**
     * Проверяет введённый код подтверждения телефона.
     * Сейчас используется заглушка, ожидается код "5678".
     *
     * @param code введённый пользователем код
     * @return true, если код корректный
     */
    private boolean isValidPhoneCode(String code) {
        // TODO: заменить на настоящую проверку
        return "5678".equals(code);
    }

}