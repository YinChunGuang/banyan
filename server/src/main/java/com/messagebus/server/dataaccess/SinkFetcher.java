package com.messagebus.server.dataaccess;

import com.messagebus.business.exchanger.IDataFetcher;
import com.messagebus.business.model.Sink;
import com.messagebus.common.Constants;
import com.messagebus.common.ExceptionHelper;
import com.messagebus.interactor.pubsub.IDataConverter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Created by yanghua on 3/16/15.
 */
public class SinkFetcher implements IDataFetcher {

    private static final Log logger = LogFactory.getLog(SinkFetcher.class);

    private DBAccessor dbAccessor;

    public SinkFetcher(DBAccessor dbAccessor) {
        this.dbAccessor = dbAccessor;
    }

    @Override
    public byte[] fetchData(IDataConverter converter) {
        ArrayList<Sink> sinks = new ArrayList<Sink>();
        String sql = "SELECT * FROM SINK WHERE AUDIT_TYPE_CODE = '" + Constants.AUDIT_TYPE_CODE_SUCCESS + "'"
            + " AND FROM_COMMUNICATE_TYPE NOT IN ('publish','publish-subscribe') AND TO_COMMUNICATE_TYPE NOT IN ('subscribe')";

        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet rs = null;
        try {
            connection = this.dbAccessor.getConnection();
            statement = connection.prepareStatement(sql);
            rs = statement.executeQuery();
            while (rs.next()) {
                Sink sink = new Sink();
                sink.setToken(rs.getString("TOKEN"));
                sink.setFlowFrom(rs.getString("FLOW_FROM"));
                sink.setFlowTo(rs.getString("FLOW_TO"));
                sinks.add(sink);
            }
        } catch (SQLException e) {
            ExceptionHelper.logException(logger, e, "fetchData");
            throw new RuntimeException(e);
        } finally {
            try {
                if (rs != null) rs.close();
                if (statement != null) statement.close();
                if (connection != null) connection.close();
            } catch (SQLException e) {

            }
        }

        Sink[] sinkArr = sinks.toArray(new Sink[sinks.size()]);

        return converter.serialize(sinkArr);
    }
}
