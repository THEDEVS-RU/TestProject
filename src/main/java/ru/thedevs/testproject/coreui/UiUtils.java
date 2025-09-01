package ru.thedevs.testproject.coreui;

import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.server.StreamResource;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.View;
import org.springframework.stereotype.Component;
import ru.thedevs.entities.UserEntity;
import ru.thedevs.service.ICoreUtilsService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Component("tp_UiUtils")
public class UiUtils {
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

    public HorizontalLayout createUserIndicator() {
        HorizontalLayout box = uiComponents.create(HorizontalLayout.class);
        UserEntity user = coreUtilsService.getCurrentEffectiveUser();
        if (user == null) {
            return box;
        }

        Avatar avatar = createUserAvatar(user);

        JmixButton nameButton = getDefaultButton(null, "tertiary-inline",
                user.getDisplayName(), e -> openUserDetail(user));

        box.add(avatar, nameButton);
        return box;
    }

    private Avatar createUserAvatar(UserEntity user) {
        Avatar avatar = new Avatar(user.getDisplayName());
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
            }
        }

        return avatar;
    }

    private void openUserDetail(UserEntity user) {
        View<?> currentView = UiComponentUtils.getCurrentView();
        viewNavigators.detailView(currentView, UserEntity.class)
                .editEntity(user)
                .withBackwardNavigation(true)
                .navigate();
    }
}