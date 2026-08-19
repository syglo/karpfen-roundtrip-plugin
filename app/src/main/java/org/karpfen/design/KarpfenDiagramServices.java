package org.karpfen.design;

import java.util.List;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.sirius.viewpoint.DSemanticDecorator;

public class KarpfenDiagramServices {

    // Only visual representation!
    // Required for .kmodel diagram names to be rendered correctly (they are dynamic
    // emf objects, aql limitations)
    // For D2T they are not required, by selecting object box we can directly inside
    // it edit values of variables
    // Header label for the object box id:type / type
    // Labels for slots attr = value
    public String getObjectLabel(EObject self) {
        if (self == null || self.eClass() == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        // header
        EStructuralFeature idFeature = self.eClass().getEStructuralFeature("__id__");
        Object idVal = (idFeature != null) ? self.eGet(idFeature) : null;
        String idStr = (idVal != null) ? idVal.toString().trim() : "";

        if (!idStr.isEmpty()) {
            sb.append(idStr).append(" : ").append(self.eClass().getName());
        } else {
            sb.append(self.eClass().getName());
        }

        // slots
        List<EAttribute> attributes = self.eClass().getEAllAttributes();
        boolean hasSlots = false;
        for (EAttribute attr : attributes) {
            if (!"__id__".equals(attr.getName())) {
                hasSlots = true;
                break;
            }
        }

        if (hasSlots) {
            sb.append("\n────────────────────");
            for (EAttribute attr : attributes) {
                if ("__id__".equals(attr.getName())) {
                    continue;
                }
                sb.append("\n").append(attr.getName()).append(" = ");
                Object val = self.eGet(attr);
                if (val != null) {
                    sb.append(val.toString());
                } else {
                    sb.append("null");
                }
            }
        }

        return sb.toString();
    }
}