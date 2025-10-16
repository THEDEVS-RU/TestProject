package ru.thedevs.testproject.coreui;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.contextmenu.ContextMenu;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.server.StreamResource;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import io.jmix.core.usersubstitution.CurrentUserSubstitution;
import io.jmix.core.usersubstitution.UserSubstitutionManager;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.component.UiComponentUtils;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.View;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.security.core.userdetails.UserDetails;
import ru.thedevs.entities.UserEntity;
import ru.thedevs.service.ICoreUtilsService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Component("tp_UiUtils")
public class UiUtils {
    private final UiComponents uiComponents;
    private final ICoreUtilsService coreUtilsService;
    private final ViewNavigators viewNavigators;
    private final FileStorageLocator fileStorageLocator;

    @Autowired(required = false)
    private UserSubstitutionManager userSubstitutionManager;

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
        List<UserDetails> substitutable = null;
        if (userSubstitutionManager != null) {
            substitutable = userSubstitutionManager.getCurrentSubstitutedUsers();
            System.out.println(substitutable);
        }
        if (substitutable == null || substitutable.isEmpty()) {
            HorizontalLayout box = uiComponents.create(HorizontalLayout.class);
            box.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
            UserEntity user = coreUtilsService.getCurrentEffectiveUser();
            if (user == null) {
                return box;
            }

            Avatar avatar = createUserAvatar(user);

            String shortFio = formatShortFio(user);
            avatar.getElement().getStyle().set("cursor", "pointer");
            avatar.getElement().setAttribute("title", shortFio);
            avatar.getElement().addEventListener("click", evt -> openUserDetail(user));

            JmixButton nameButton = getDefaultButton(null, "tertiary-inline",
                    shortFio, null);

            if (shortFio != null) {
                nameButton.getElement().setAttribute("title", shortFio);
            }

            String maxWidth = "200px";
            nameButton.getElement().getStyle().set("max-width", maxWidth);
            nameButton.getElement().getStyle().set("overflow", "hidden");
            nameButton.getElement().getStyle().set("text-overflow", "ellipsis");
            nameButton.getElement().getStyle().set("white-space", "nowrap");
            nameButton.getElement().getStyle().set("display", "inline-block");


            nameButton.addClickListener(e -> openUserDetail(user));

            box.add(avatar, nameButton);
            return box;
        }
        return null;
    }

    private Avatar createUserAvatar(UserEntity user) {
        Avatar avatar = new Avatar(formatShortFio(user));
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

    private String formatShortFio(UserEntity user) {
        if (user == null) return "";
        String last = user.getLastName();
        String first = user.getFirstName();
        String middle = user.getMiddleName();
        if (last == null || last.isBlank()) {
            return user.getDisplayedName() != null ? user.getDisplayedName() : "";
        }
        StringBuilder sb = new StringBuilder(last.trim());
        if (first != null && !first.isBlank()) {
            sb.append(" ").append(first.trim().substring(0, 1).toUpperCase()).append(".");
        }
        if (middle != null && !middle.isBlank()) {
            sb.append(" ").append(middle.trim().substring(0, 1).toUpperCase()).append(".");
        }
        return sb.toString().trim();
    }


}
