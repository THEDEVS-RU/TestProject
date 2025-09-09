package ru.thedevs.testproject;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.server.StreamResource;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import io.jmix.core.Messages;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.View;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.thedevs.entities.UserEntity;
import ru.thedevs.entities.marker.ICoreEntity;
import ru.thedevs.service.ICoreUtilsService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.function.Consumer;

@Component("tp_UiUtils")
public class UiUtils {

    @Autowired
    private Messages messages;

    public static final String AVATAR_ID = "avatar";
    public static final String PROFILE_ID = "profile";
    private final UiComponents uiComponents;
    private final ICoreUtilsService coreUtilsService;
    private final ViewNavigators viewNavigators;
    private final FileStorageLocator fileStorageLocator;

    public UiUtils(UiComponents uiComponents, ICoreUtilsService coreUtilsService, ViewNavigators viewNavigators, FileStorageLocator fileStorageLocator) {
        this.uiComponents = uiComponents;
        this.coreUtilsService = coreUtilsService;
        this.viewNavigators = viewNavigators;
        this.fileStorageLocator = fileStorageLocator;
    }

    public static BigDecimal getValueBySurchange(BigDecimal value, BigDecimal surchange) {
        if (value != null && surchange != null && surchange.compareTo(BigDecimal.ZERO) != 0) {
            if (value.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal surcharge = surchange.setScale(8, RoundingMode.UP);
                BigDecimal onePercent = value.setScale(8, RoundingMode.DOWN).divide(new BigDecimal("100"), RoundingMode.UP);
                return onePercent.multiply(surcharge).add(value).setScale(2, RoundingMode.UP);
            } else {
                return BigDecimal.ZERO;
            }
        }
        return value;
    }

    public HorizontalLayout getDefaultHbWithIconAndCaption(String iconName, String iconStyleName, String caption, String labelStyleName) {
        HorizontalLayout defaultHb = getDefaultHbWithIcon(iconName, iconStyleName);
        Span label = uiComponents.create(Span.class);
        label.setText(caption);
        label.addClassName(labelStyleName);
        defaultHb.add(label);
        return defaultHb;
    }

    public HorizontalLayout getDefaultHbWithIcon(String iconName, String iconStyleName) {
        HorizontalLayout defaultHb = uiComponents.create(HorizontalLayout.class);
        defaultHb.add(getDefaultButton(iconName, iconStyleName, null, null));
        return defaultHb;
    }

    public JmixButton getDefaultButton(String iconName, String styleName,
                                       String userNameOnDisplay, ComponentEventListener
                                               <ClickEvent<Button>> clickEvent) {
        JmixButton button = uiComponents.create(JmixButton.class);
        button.setThemeName(styleName == null ? "icon-only" : "icon-only" + " " + styleName);
        button.setEnabled(true);
        if (iconName != null) {
            button.setIcon(new Icon(iconName));
        }
        if (userNameOnDisplay != null) {
            button.setText(userNameOnDisplay);
        }
        if (clickEvent != null) {
            button.addClickListener(clickEvent);
        }
        return button;
    }

    public Long getDifferenceBetweenNowAndLastPositionDate(Long lastPosition) {
        if (lastPosition != null) {
            long threshold = Instant.now().getEpochSecond();
            long difference = threshold - lastPosition;
            return difference / (60 * 1000);
        }
        return null;
    }

    public JmixButton getUserIndicator() {
        UserEntity user = coreUtilsService.getCurrentEffectiveUser();
        if (user == null) {
            return null;
        }
        JmixButton button = getDefaultButton(null, "tertiary-inline",
                user.getDisplayedName(), e -> openDetail(user));
        button.setId(PROFILE_ID);
        return button;
    }

    public Avatar createUserAvatar() {
        UserEntity user = coreUtilsService.getCurrentEffectiveUser();
        Avatar avatar = new Avatar(user.getDisplayedName());
        avatar.setThemeName("xsmall");

        FileRef picture = user.getPicture();
        if (picture != null) {
            try {
                FileStorage fileStorage = fileStorageLocator.getDefault();

                StreamResource resource = new StreamResource(
                        picture.getFileName(),
                        () -> fileStorage.openStream(picture)
                );

                avatar.setImageResource(resource);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        avatar.setId(AVATAR_ID);
        return avatar;
    }

    @SuppressWarnings("unchecked")
    private <T extends ICoreEntity> void openDetail(T entity) {
        View<?> currentView = UiComponentUtils.getCurrentView();
        viewNavigators.detailView(currentView, (Class<T>) entity.getClass())
                .editEntity(entity)
                .withBackwardNavigation(true)
                .navigate();
    }

    public Dialog getDefaultDialog(
            String title,
            Runnable onSave,
            Runnable onCancel
    ) {
        Dialog dialog = uiComponents.create(Dialog.class);
        dialog.setWidth("420px");
        dialog.setModal(true);
        dialog.setCloseOnEsc(false);
        dialog.setCloseOnOutsideClick(false);

        VerticalLayout layout = new VerticalLayout();
        layout.setWidthFull();
        layout.setSpacing(true);
        layout.setPadding(true);

// выравниваем всё содержимое по центру
        layout.setDefaultHorizontalComponentAlignment(FlexComponent.Alignment.CENTER);

        Span titleLabel = new Span(title);
        titleLabel.getStyle().set("font-weight", "bold");
        titleLabel.getStyle().set("text-align", "center"); // сам текст тоже по центру
        titleLabel.setWidthFull();
        layout.add(titleLabel);

        HorizontalLayout buttons = new HorizontalLayout();
        buttons.setWidthFull();
        buttons.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER); // кнопки тоже по центру

        Button saveButton = new Button(
                messages.getMessage("ru.thedevs.coreui.view.user/ConfirmBtn"),
                e -> {
                    dialog.close();
                    if (onSave != null) {
                        onSave.run();
                    }
                }
        );

        Button cancelButton = new Button(
                messages.getMessage("ru.thedevs.coreui.view.user/CancelBtn"),
                e -> {
                    dialog.close();
                    if (onCancel != null) {
                        onCancel.run();
                    }
                }
        );

        buttons.add(saveButton, cancelButton);
        layout.add(buttons);

        dialog.add(layout);
        return dialog;

    }
}

