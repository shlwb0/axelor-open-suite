package com.axelor.apps.sale.service;

import com.axelor.apps.base.db.Product;
import com.axelor.apps.sale.db.Configurator;
import com.axelor.apps.sale.db.ConfiguratorCreator;
import com.axelor.apps.sale.db.ConfiguratorFormula;
import com.axelor.apps.sale.db.repo.ConfiguratorCreatorRepository;
import com.axelor.apps.sale.db.repo.ConfiguratorRepository;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaJsonField;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import java.util.ArrayList;
import java.util.List;

public class ConfiguratorCreatorServiceImpl implements ConfiguratorCreatorService {

    private ConfiguratorRepository configuratorRepo;
    private ConfiguratorCreatorRepository configuratorCreatorRepo;

    @Inject
    public ConfiguratorCreatorServiceImpl(ConfiguratorRepository configuratorRepo, ConfiguratorCreatorRepository configuratorCreatorRepo) {
        this.configuratorRepo = configuratorRepo;
        this.configuratorCreatorRepo = configuratorCreatorRepo;
    }


    @Override
    @Transactional
    public Configurator generateConfigurator(ConfiguratorCreator creator) {

        if (creator == null) {
            return null;
        }
        updateAttributesAttrs(creator.getAttributes());
        for (MetaJsonField field : creator.getAttributes()) {
            String condition = "$record.configuratorCreator.id == " + creator.getId();
            String showIf = field.getShowIf();
            if (!Strings.isNullOrEmpty(showIf)) {
                if (!showIf.contains(condition)) {
                    field.setShowIf(condition + " && (" + showIf + ")");
                }
            }
            else {
                field.setShowIf(condition);
            }
        }
        Configurator configurator =  configuratorRepo.all().filter("self.configuratorCreator = ?1", creator).fetchOne();

        if (configurator == null) {
            configurator = new Configurator();
            configurator.setConfiguratorCreator(creator);
            configuratorRepo.save(configurator);
        }


        return configurator;
    }

    @Transactional(rollbackOn = {Exception.class})
    public void updateIndicators(ConfiguratorCreator creator) {
        List<ConfiguratorFormula> formulas = creator.getFormulas();
        List<MetaJsonField> indicators = creator.getIndicators();

        //add missing formulas
        if (formulas != null) {
            for (ConfiguratorFormula formula : formulas) {
                addIfMissing(formula, creator);
            }
        }

        //remove formulas
        List<MetaJsonField> fieldsToRemove = new ArrayList<>();
        for (MetaJsonField indicator : indicators) {
            if (indicator.getModel().equals(Product.class.getName())
                    && isNotInFormulas(indicator, formulas)) {
                fieldsToRemove.add(indicator);
            }
        }
        for (MetaJsonField indicatorToRemove : fieldsToRemove ) {
            creator.removeIndicator(indicatorToRemove);
        }

        updateIndicatorsAttrs(indicators, formulas);

        configuratorCreatorRepo.save(creator);
    }

    /**
     * Add the {@link ConfiguratorFormula} in {@link ConfiguratorCreator#indicators}
     * if the formula is not represented by an existing indicator.
     * @param formula
     * @param creator
     */
    protected void addIfMissing(ConfiguratorFormula formula, ConfiguratorCreator creator) {
        List<MetaJsonField> fields = creator.getIndicators();
        for (MetaJsonField field : fields) {
            if (field.getName().equals(formula.getProductField().getName())) {
                return;
            }
        }
        MetaJsonField newField = new MetaJsonField();
        newField.setModel(Configurator.class.getName());
        newField.setModelField("indicators");
        String typeName = Beans.get(MetaFieldRepository.class).all()
                .filter("self.metaModel.name = 'Product' AND " +
                        "self.name = ?", formula.getProductField().getName())
                .fetchOne().getTypeName();
        newField.setType(typeToJsonType(typeName));
        newField.setName(formula.getProductField().getName());
        creator.addIndicator(newField);
    }

    /**
     *
     * @param field
     * @param formulas
     * @return false if field is represented in formula list
     *         true if field is missing in the formula list
     */
    protected boolean isNotInFormulas(MetaJsonField field, List<ConfiguratorFormula> formulas) {
        for (ConfiguratorFormula formula : formulas) {
            if (formula.getProductField().getName().equals(field.getName())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Convert the type of a field to a type of a json field.
     * @param nameType type of a field
     * @return corresponding type of json field
     */
    protected String typeToJsonType(String nameType) {
        if (nameType.equals("BigDecimal")) {
            return "decimal";
        } else {
            return nameType.toLowerCase();
        }
    }

    protected void updateIndicatorsAttrs(List<MetaJsonField> indicators,
                                         List<ConfiguratorFormula> formulas) {
        for (MetaJsonField indicator : indicators) {
            for (ConfiguratorFormula formula : formulas) {
                if (formula.getProductField().getName().equals(indicator.getName())
                        && !formula.getShowOnConfigurator()) {
                    indicator.setShowIf("false");
                }
            }
        }
    }

    /**
     * Update attributes on creating.
     * @param attributes
     */
    protected void updateAttributesAttrs(List<MetaJsonField> attributes) {
        for (MetaJsonField attribute : attributes) {
            String onChange = attribute.getOnChange();
            if (onChange == null
                    || !onChange.contains("save,action-configurator-update-indicators")) {
                String modifiedOnChange = "save,action-configurator-update-indicators";
                if (!"".equals(onChange)) {
                    modifiedOnChange = modifiedOnChange + "," + onChange;
                }
                attribute.setOnChange(modifiedOnChange);
            }
        }
    }
}
