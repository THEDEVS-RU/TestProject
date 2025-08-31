package ru.thedevs.testproject.coreui;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
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
        defaultHb.add(getDefaultIconButton(iconName, iconStyleName));
        return defaultHb;
    }

    public JmixButton getDefaultIconButton(String iconName, String styleName) {
        JmixButton button = uiComponents.create(JmixButton.class);
        button.setThemeName(styleName == null ? "icon-only" : "icon-only" + " " + styleName);
        button.setEnabled(true);
        button.setIcon(new Icon(iconName));
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

        JmixButton nameButton = getDefaultIconButton("vaadin:user", "tertiary-inline");
        nameButton.setText(user.getInstanceName());
        nameButton.addClickListener(e -> openUserDetail(user));
        box.add(nameButton);
        return box;
    }

    private void openUserDetail(UserEntity user) {
        View<?> currentView = UiComponentUtils.getCurrentView();
        viewNavigators.detailView(currentView, UserEntity.class)
                .editEntity(user)
                .withBackwardNavigation(true)
                .navigate();
    }
}