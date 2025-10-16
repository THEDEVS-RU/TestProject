package ru.thedevs.testproject.view.main;

import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.app.main.StandardMainView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import ru.thedevs.testproject.coreui.UiUtils;

@Route("")
@ViewController(id = "TP_MainView")
@ViewDescriptor(path = "main-view.xml")
public class MainView extends StandardMainView {

    @ViewComponent
    private HorizontalLayout userIndicatorEventListener;

    private final UiUtils uiUtils;

    @Autowired
    public MainView(UiUtils uiUtils) {
        this.uiUtils = uiUtils;
    }

    @Subscribe
    public void onInit(InitEvent event) {
        if (uiUtils.createUserIndicator() != null) {
            userIndicatorEventListener.removeAll();
            userIndicatorEventListener.add(uiUtils.createUserIndicator());
        }
    }
}

