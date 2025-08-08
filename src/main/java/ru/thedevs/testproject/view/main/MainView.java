package ru.thedevs.testproject.view.main;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.app.main.StandardMainView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route("")
@ViewController(id = "TP_MainView")
@ViewDescriptor(path = "main-view.xml")
public class MainView extends StandardMainView {
}
