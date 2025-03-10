package org.apache.iotdb.admin.service.impl;

import org.apache.iotdb.admin.common.exception.BaseException;
import org.apache.iotdb.admin.common.exception.ErrorCode;
import org.apache.iotdb.admin.model.dto.IotDBRole;
import org.apache.iotdb.admin.model.dto.IotDBUser;
import org.apache.iotdb.admin.model.dto.Timeseries;
import org.apache.iotdb.admin.model.entity.Connection;
import org.apache.iotdb.admin.model.vo.IotDBUserVO;
import org.apache.iotdb.admin.model.vo.RoleWithPrivilegesVO;
import org.apache.iotdb.admin.model.vo.SqlResultVO;
import org.apache.iotdb.admin.service.IotDBService;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;


@Service
public class IotDBServiceImpl implements IotDBService {

    @Override
    public List<String> getAllStorageGroups(Connection connection) throws BaseException {
        java.sql.Connection conn = getConnection(connection);
        String sql = "show storage group";
        List<String> users = customExecuteQuery(conn, sql);
        closeConnection(conn);
        return users;
    }

    @Override
    public void saveStorageGroup(Connection connection, String groupName) throws BaseException {
        paramValid(groupName);
        java.sql.Connection conn = getConnection(connection);
        String sql = "set storage group to " + groupName;
        customExecute(conn, sql);
        closeConnection(conn);
    }

    @Override
    public void deleteStorageGroup(Connection connection, String groupName) throws BaseException {
        paramValid(groupName);
        java.sql.Connection conn = getConnection(connection);
        String sql = "delete storage group " + groupName;
        customExecute(conn, sql);
        closeConnection(conn);
    }

    @Override
    public List<String> getDevicesByGroup(Connection connection, String groupName) throws BaseException {
        paramValid(groupName);
        java.sql.Connection conn = getConnection(connection);
        String sql = "show devices " + groupName;
        List<String> devices = customExecuteQuery(conn, sql);
        closeConnection(conn);
        return devices;
    }

    @Override
    public List<String> getMeasurementsByDevice(Connection connection, String deviceName) throws BaseException {
        paramValid(deviceName);
        java.sql.Connection conn = getConnection(connection);
        String sql = "show child paths " + deviceName;
        List<String> measurements = customExecuteQuery(conn, sql);
        closeConnection(conn);
        return measurements;
    }

    @Override
    public List<String> getIotDBUserList(Connection connection) throws BaseException {
        java.sql.Connection conn = getConnection(connection);
        String sql = "list user";
        List<String> users = customExecuteQuery(conn, sql);
        closeConnection(conn);
        return users;
    }

    @Override
    public List<String> getIotDBRoleList(Connection connection) throws BaseException {
        java.sql.Connection conn = getConnection(connection);
        String sql = "list role";
        List<String> roles = customExecuteQuery(conn, sql);
        closeConnection(conn);
        return roles;
    }

    @Override
    public IotDBUserVO getIotDBUser(Connection connection, String userName) throws BaseException {
        paramValid(userName);
        java.sql.Connection conn = getConnection(connection);
        IotDBUserVO iotDBUserVO = new IotDBUserVO();
        iotDBUserVO.setUserName(userName);
        String sql = "list user privileges " + userName;
        List<RoleWithPrivilegesVO> roleWithPrivileges = customExecuteQuery(RoleWithPrivilegesVO.class, conn, sql);
        closeConnection(conn);
        iotDBUserVO.setRoleWithPrivileges(roleWithPrivileges);
        return iotDBUserVO;
    }

    @Override
    public void deleteIotDBUser(Connection connection, String userName) throws BaseException {
        paramValid(userName);
        java.sql.Connection conn = getConnection(connection);
        String sql = "drop user " + userName;
        customExecute(conn, sql);
        closeConnection(conn);
    }

    @Override
    public void deleteIotDBRole(Connection connection, String roleName) throws BaseException {
        paramValid(roleName);
        java.sql.Connection conn = getConnection(connection);
        String sql = "drop role " + roleName;
        customExecute(conn, sql);
        closeConnection(conn);
    }

    @Override
    public void setIotDBUser(Connection connection, IotDBUser iotDBUser) throws BaseException {
        java.sql.Connection conn = getConnection(connection);
        String userName = iotDBUser.getUserName();
        String password = iotDBUser.getPassword();
        String sql = "create user " + userName + " '" + password + "'";
        customExecute(conn, sql);
        // 用户角色
        for (String role : iotDBUser.getRoles()) {
            paramValid(role);
            sql = "grant " + role + " to " + userName;
            customExecute(conn, sql);
        }
        // 用户授权
        List<String> privileges = iotDBUser.getPrivileges();
        for (String privilege : privileges) {
            sql = handlerPrivilegeStrToSql(privilege, userName, null);
            if (sql != null) {
                customExecute(conn, sql);
            }
        }
        closeConnection(conn);
    }

    @Override
    public void setIotDBRole(Connection connection, IotDBRole iotDBRole) throws BaseException {
        java.sql.Connection conn = getConnection(connection);
        String roleName = iotDBRole.getRoleName();
        String sql = "create role " + roleName;
        customExecute(conn, sql);
        List<String> privileges = iotDBRole.getPrivileges();
        for (String privilege : privileges) {
            sql = handlerPrivilegeStrToSql(privilege, null, roleName);
            if (sql != null) {
                customExecute(conn, sql);
            }
        }
        closeConnection(conn);
    }

    @Override
    public SqlResultVO query(Connection connection, String sql) throws BaseException {
        java.sql.Connection conn = getConnection(connection);
        SqlResultVO sqlResultVO = sqlQuery(conn, sql);
        closeConnection(conn);
        return sqlResultVO;
    }

    @Override
    public void insertTimeseries(Connection connection, String deviceName, Timeseries timeseries) throws BaseException {
        SessionPool session = getSession(connection);
        try {
            List<TSDataType> types = handleTypeStr(timeseries.getTypes());
            List<Object> values = handleValueStr(timeseries.getValues(),types);
            session.insertRecord(deviceName,timeseries.getTime(),timeseries.getMeasurements(),types,values);
        } catch (IoTDBConnectionException e) {
            throw new BaseException(ErrorCode.INSERT_TS_FAIL, ErrorCode.INSERT_TS_FAIL_MSG);
        } catch (StatementExecutionException e) {
            throw new BaseException(ErrorCode.INSERT_TS_FAIL, ErrorCode.INSERT_TS_FAIL_MSG);
        }finally {
            if(session != null){
                session.close();
            }
        }

    }

    @Override
    public void deleteTimeseries(Connection connection, String timeseriesName) throws BaseException {
        SessionPool session = getSession(connection);
        try {
            session.deleteTimeseries(timeseriesName);
        } catch (IoTDBConnectionException e) {
            throw new BaseException(ErrorCode.DELETE_TS_FAIL, ErrorCode.DELETE_TS_FAIL_MSG);
        } catch (StatementExecutionException e) {
            throw new BaseException(ErrorCode.DELETE_TS_FAIL, ErrorCode.DELETE_TS_FAIL_MSG);
        }
        session.close();
    }

    @Override
    public SqlResultVO showTimeseries(Connection connection, String deviceName) throws BaseException {
        paramValid(deviceName);
        java.sql.Connection conn = getConnection(connection);
        String sql = "show timeseries " + deviceName;
        SqlResultVO resultVO = sqlQuery(conn, sql);
        closeConnection(conn);
        return resultVO;
    }

    private List<Object> handleValueStr(List<String> values, List<TSDataType> types) throws BaseException {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            TSDataType type = types.get(i);
            if(type == TSDataType.BOOLEAN){
                Integer booleanNum = Integer.valueOf(values.get(i));
                Boolean flag = null;
                if(booleanNum == 0){
                    flag = false;
                }
                if(booleanNum == 1){
                    flag = true;
                }
                if(flag != null){
                    list.add(flag);
                    continue;
                }
                throw new BaseException(ErrorCode.DB_BOOL_WRONG, ErrorCode.DB_BOOL_WRONG_MSG);
            }
            if(type == TSDataType.INT32 || type == TSDataType.INT64){
                Integer intNum = Integer.valueOf(values.get(i));
                list.add(intNum);
                continue;
            }
            if(type == TSDataType.FLOAT){
                Float floatNum = Float.valueOf(values.get(i));
                list.add(floatNum);
                continue;
            }
            if(type == TSDataType.DOUBLE){
                Double doubleNum = Double.valueOf(values.get(i));
                list.add(doubleNum);
                continue;
            }
            list.add(values.get(i));
        }
        return list;
    }

    private List<TSDataType> handleTypeStr(List<String> types) throws BaseException {
        List<TSDataType> list = new ArrayList<>();
        for (String type : types) {
            TSDataType tsDataType;
            switch (type){
                case "BOOLEAN":
                    tsDataType = TSDataType.BOOLEAN;
                    break;
                case "INT32":
                    tsDataType = TSDataType.INT32;
                    break;
                case "INT64":
                    tsDataType = TSDataType.INT64;
                    break;
                case "FLOAT":
                    tsDataType = TSDataType.FLOAT;
                    break;
                case "DOUBLE":
                    tsDataType = TSDataType.DOUBLE;
                    break;
                case "TEXT":
                    tsDataType = TSDataType.TEXT;
                    break;
                default:
                    throw new BaseException(ErrorCode.DB_DATATYPE_WRONG,ErrorCode.DB_DATATYPE_WRONG_MSG);
            }
            list.add(tsDataType);
        }
        return list;
    }


    public static java.sql.Connection getConnection(Connection connection) throws BaseException {
        String driver = "org.apache.iotdb.jdbc.IoTDBDriver";
        String url = "jdbc:iotdb://" + connection.getHost() + ":" + connection.getPort() + "/";
        String username = connection.getUsername();
        String password = connection.getPassword();
        java.sql.Connection conn = null;
        try {
            Class.forName(driver);
            conn = DriverManager.getConnection(url, username, password);
        } catch (ClassNotFoundException e) {
            throw new BaseException(ErrorCode.GET_DBCONN_FAIL,ErrorCode.GET_DBCONN_FAIL_MSG);
        } catch (SQLException e) {
            throw new BaseException(ErrorCode.GET_DBCONN_FAIL,ErrorCode.GET_DBCONN_FAIL_MSG);
        }
        return conn;
    }

        public static SessionPool getSession(Connection connection) throws BaseException {
            String host = connection.getHost();
            Integer port = connection.getPort();
            String username = connection.getUsername();
            String password = connection.getPassword();
            SessionPool sessionPool = null;
            try {
                sessionPool = new SessionPool(host,port,username,password,3);
            } catch (Exception e) {
                throw new BaseException(ErrorCode.GET_SESSION_FAIL,ErrorCode.GET_SESSION_FAIL_MSG);
            }
            return sessionPool;
        }
//    public static SessionPool getSession(Connection connection) throws BaseException {
//        if(sessionPool == null){
//            host = connection.getHost();
//            port = connection.getPort();
//            username = connection.getUsername();
//            password = connection.getPassword();
//            sessionPool = new SessionPool(host,port,username,password,3);
//            return sessionPool;
//        }
//        if(host == connection.getHost() && port.equals(connection.getPort()) && username == connection.getUsername() && password == connection.getPassword()){
//            return sessionPool;
//        }
//        sessionPool.close();
//        host = connection.getHost();
//        port = connection.getPort();
//        username = connection.getUsername();
//        password = connection.getPassword();
//        sessionPool = new SessionPool(host,port,username,password,3);
//        return sessionPool;
//    }

    private void closeConnection(java.sql.Connection conn) throws BaseException {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            throw new BaseException(ErrorCode.CLOSE_DBCONN_FAIL,ErrorCode.CLOSE_DBCONN_FAIL_MSG);
        }
    }

    private String handlerPrivilegeStrToSql(String privilege, String userName, String roleName) {
        int i = privilege.indexOf(":");
        String path = privilege.substring(0, i).trim();
        String[] privileges = privilege.substring(i + 1).trim().split(" ");
        int len = privileges.length;
        if (len == 0) {
            return null;
        }
        StringBuilder str = new StringBuilder();
        if (userName != null) {
            str.append("grant user " + userName + " privileges ");
        } else {
            str.append("grant role " + roleName + " privileges ");
        }
        for (int j = 0; i < len - 1; j++) {
            str.append("'" + privileges[j] + "',");
        }
        str.append("'" + privileges[len - 1] + "' on " + path);
        return str.toString();
    }

    private void customExecute(java.sql.Connection conn, String sql) throws BaseException {
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.execute();
        } catch (SQLException e) {
            throw new BaseException(ErrorCode.SQL_EP,ErrorCode.SQL_EP_MSG);
        } finally {
            if (preparedStatement != null) {
                try {
                    preparedStatement.close();
                } catch (SQLException e) {
                    throw new BaseException(ErrorCode.SQL_EP,ErrorCode.SQL_EP_MSG);
                }
            }
            closeConnection(conn);
        }
    }

    private List<String> customExecuteQuery(java.sql.Connection conn, String sql) throws BaseException {
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            statement = conn.prepareStatement(sql);
            resultSet = statement.executeQuery();
            int columnCount = resultSet.getMetaData().getColumnCount();
            List<String> list = new ArrayList<>();
            while (resultSet.next()) {
                for (int i = 0; i < columnCount; i++) {
                    list.add(resultSet.getString(i + 1));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new BaseException(ErrorCode.SQL_EP,ErrorCode.SQL_EP_MSG);
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException e) {
                    throw new BaseException(ErrorCode.SQL_EP,ErrorCode.SQL_EP_MSG);
                }
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    throw new BaseException(ErrorCode.SQL_EP,ErrorCode.SQL_EP_MSG);
                }
            }
            closeConnection(conn);
        }
    }

    private <T> List<T> customExecuteQuery(Class<T> clazz, java.sql.Connection conn, String sql) throws BaseException {
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            statement = conn.prepareStatement(sql);
            resultSet = statement.executeQuery();
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            List<T> list = new ArrayList<>();
            while (resultSet.next()) {
                T t = clazz.newInstance();
                for (int i = 0; i < columnCount; i++) {
                    Object value = resultSet.getObject(i + 1);
                    String columnName = metaData.getColumnLabel(i + 1);
                    Field field = clazz.getDeclaredField(columnName);
                    field.setAccessible(true);
                    field.set(t, value);
                }
                list.add(t);
            }
            return list;
        } catch (Exception e) {
            throw new BaseException(ErrorCode.QUERY_FAIL,ErrorCode.QUERY_FAIL_MSG);
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException e) {
                    throw new BaseException(ErrorCode.QUERY_FAIL,ErrorCode.QUERY_FAIL_MSG);
                }
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    throw new BaseException(ErrorCode.QUERY_FAIL,ErrorCode.QUERY_FAIL_MSG);
                }
            }
            closeConnection(conn);
        }
    }

    private SqlResultVO sqlQuery(java.sql.Connection conn, String sql) throws BaseException {
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        try {
            statement = conn.prepareStatement(sql);
            resultSet = statement.executeQuery();
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            SqlResultVO sqlResultVO = new SqlResultVO();
            List<String> metaDataList = new ArrayList<>();
            for (int i = 0; i < columnCount; i++) {
                metaDataList.add(metaData.getColumnLabel(i + 1));
            }
            sqlResultVO.setMetaDataList(metaDataList);
            List<List<String>> valuelist = new ArrayList<>();
            while (resultSet.next()) {
                List<String> strList = new ArrayList<>();
                for (int i = 0; i < columnCount; i++) {
                    strList.add(resultSet.getString(i + 1));
                }
                valuelist.add(strList);
            }
            sqlResultVO.setValueList(valuelist);
            return sqlResultVO;
        } catch (SQLException e) {
            throw new BaseException(ErrorCode.SQL_EP,ErrorCode.SQL_EP_MSG);
        } finally {
            if (resultSet != null) {
                try {
                    resultSet.close();
                } catch (SQLException e) {
                    throw new BaseException(ErrorCode.SQL_EP,ErrorCode.SQL_EP_MSG);
                }
            }
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    throw new BaseException(ErrorCode.SQL_EP,ErrorCode.SQL_EP_MSG);
                }
            }
            closeConnection(conn);
        }
    }

    /**
     * 防止sql注入对参数进行校验不能有空格
     * @param field 拼接sql的字段
     */
    private void paramValid(String field) throws BaseException {
        if(field != null){
            if (!field.matches("^[^ ]+$")) {
                throw new BaseException(ErrorCode.SQL_PARAM_WRONG,ErrorCode.SQL_PARAM_WRONG_MSG);
            }
        }
    }
}