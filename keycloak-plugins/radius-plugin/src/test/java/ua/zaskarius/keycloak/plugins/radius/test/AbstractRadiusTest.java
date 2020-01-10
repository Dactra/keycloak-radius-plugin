package ua.zaskarius.keycloak.plugins.radius.test;

import ua.zaskarius.keycloak.plugins.radius.configuration.ConfigurationScheduledTask;
import ua.zaskarius.keycloak.plugins.radius.configuration.IRadiusConfiguration;
import ua.zaskarius.keycloak.plugins.radius.configuration.RadiusConfigHelper;
import ua.zaskarius.keycloak.plugins.radius.models.RadiusUserInfo;
import ua.zaskarius.keycloak.plugins.radius.password.RadiusCredentialModel;
import ua.zaskarius.keycloak.plugins.radius.radius.handlers.protocols.AuthProtocol;
import ua.zaskarius.keycloak.plugins.radius.radius.handlers.protocols.AuthProtocolFactory;
import ua.zaskarius.keycloak.plugins.radius.radius.handlers.protocols.RadiusAuthProtocolFactory;
import ua.zaskarius.keycloak.plugins.radius.radius.provider.RadiusRadiusProvider;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.keycloak.common.ClientConnection;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.*;
import org.keycloak.provider.Provider;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.tinyradius.dictionary.DictionaryParser;
import org.tinyradius.dictionary.WritableDictionary;
import ua.zaskarius.keycloak.plugins.radius.mappers.RadiusPasswordMapper;
import ua.zaskarius.keycloak.plugins.radius.radius.server.KeycloakRadiusServer;

import java.io.IOException;
import java.security.Security;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertNotNull;

public abstract class AbstractRadiusTest {

    {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static final String REALM_RADIUS_NAME = "RadiusName";
    public static final String CLIENT_ID = "CLIENT_ID";
    public static final String USER = "USER";
    @Mock
    protected KeycloakSession session;

    @Mock
    protected KeycloakTransactionManager keycloakTransactionManager;

    @Mock
    protected KeycloakSessionFactory keycloakSessionFactory;

    @Mock
    protected RealmModel realmModel;

    @Mock
    protected ClientModel clientModel;

    @Mock
    protected UserModel userModel;

    @Mock
    protected UserProvider userProvider;

    @Mock
    protected IRadiusConfiguration configuration;
    @Mock
    protected RoleModel radiusRole;

    @Mock
    protected UserCredentialManager userCredentialManager;

    protected UserSessionProvider userSessionProvider;
    @Mock
    protected UserSessionModel userSessionModel;

    @Mock
    protected ClientConnection clientConnection;

    @Mock
    protected AuthProtocolFactory radiusAuthProtocolFactory;
    @Mock
    protected AuthProtocol authProtocol;

    protected WritableDictionary realDictionary;

    protected RadiusUserInfo radiusUserInfo;

    protected EventBuilder eventBuilder;

    protected Map<Class, Provider> providerByClass = new HashMap<>();

    protected List<? extends Object> resetMock() {
        return null;
    }


    void resetStatic() {
        ConfigurationScheduledTask instance = (ConfigurationScheduledTask)
                ConfigurationScheduledTask.getInstance();
        instance.connectionProviderMap.clear();
        instance.flowConfigurations.clear();
        try {
            RadiusConfigHelper.setFlowConfiguration(configuration);
            RadiusAuthProtocolFactory.setInstance(radiusAuthProtocolFactory);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @BeforeMethod
    public void beforeRadiusMethod() {
        reset(session);
        reset(keycloakTransactionManager);
        reset(keycloakSessionFactory);
        reset(realmModel);
        reset(clientModel);
        reset(userModel);
        reset(userProvider);
        reset(configuration);
        reset(radiusRole);
        reset(userCredentialManager);
        reset(userSessionModel);
        reset(clientConnection);
        reset(radiusAuthProtocolFactory);
        reset(authProtocol);
        providerByClass.clear();
        Answer<Object> providerAnswer = invocation -> {
            Object parameter = invocation.getArguments()[0];
            Class<? extends Provider> classToMock = (Class<? extends Provider>) parameter;
            Provider provider = providerByClass.get(classToMock);
            if (provider == null && classToMock != null) {
                provider = mock(classToMock);
                providerByClass.put(classToMock, provider);
            }
            return provider;
        };
        Answer<Object> providerAnswers = invocation -> {
            Object parameter = invocation.getArguments()[0];
            Class<? extends Provider> classToMock = (Class<? extends Provider>) parameter;
            Provider provider = providerByClass.get(classToMock);
            if (provider == null && classToMock != null) {
                provider = mock(classToMock);
                providerByClass.put(classToMock, provider);
            }
            HashSet<Object> set = new HashSet<>();
            set.add(provider);
            return set;
        };

        when(keycloakSessionFactory.create()).thenReturn(session);
        when(session.getProvider(any())).thenAnswer(providerAnswer);
        when(session.getProvider(any(), anyString())).thenAnswer(providerAnswer);
        when(session.getAllProviders(any())).thenAnswer(providerAnswers);
        when(session.getTransactionManager()).thenReturn(keycloakTransactionManager);
        radiusUserInfo = new RadiusUserInfo();
        radiusUserInfo.setUserModel(userModel);
        radiusUserInfo.setRealmModel(realmModel);
        radiusUserInfo.setRadiusSecret("secret");
        radiusUserInfo.setPasswords(Collections.singletonList("secret"));
        radiusUserInfo.setClientConnection(clientConnection);
        radiusUserInfo.setActivePassword("secret");
        when(session.getAttribute("RADIUS_INFO", RadiusUserInfo.class)).thenReturn(radiusUserInfo);
        when(keycloakTransactionManager.isActive()).thenReturn(true);
        when(keycloakTransactionManager.getRollbackOnly()).thenReturn(false);
        when(session.userCredentialManager()).thenReturn(userCredentialManager);
        when(session.getKeycloakSessionFactory()).thenReturn(keycloakSessionFactory);
        userSessionProvider = session.getProvider(UserSessionProvider.class);
        assertNotNull(userSessionProvider);
        when(session.sessions()).thenReturn(userSessionProvider);
        RealmProvider realmProvider = getProvider(RealmProvider.class);
        when(session.realms()).thenReturn(realmProvider);
        when(clientModel.getRealm()).thenReturn(realmModel);
        when(clientModel.getClientId()).thenReturn(CLIENT_ID);
        when(realmProvider.getRealm(REALM_RADIUS_NAME)).thenReturn(realmModel);
        when(realmProvider.getRealm(anyString())).thenReturn(realmModel);
        when(realmProvider.getRealms()).thenReturn(Arrays.asList(realmModel));
        when(realmModel.getClientByClientId(CLIENT_ID)).thenReturn(clientModel);
        when(realmModel.getName()).thenReturn(REALM_RADIUS_NAME);
        when(realmModel.getId()).thenReturn(REALM_RADIUS_NAME);
        when(realmModel.isEventsEnabled()).thenReturn(false);
        when(realmModel.getRole(RadiusRadiusProvider.READ_MIKROTIK_PASSWORD))
                .thenReturn(radiusRole);
        when(session.users()).thenReturn(userProvider);
        when(userProvider.getUserByUsername(USER, realmModel)).thenReturn(userModel);
        when(userProvider.getUserByEmail(USER, realmModel)).thenReturn(userModel);
        when(userModel.getUsername()).thenReturn(USER);
        when(userModel.getEmail()).thenReturn(USER);
        when(userModel.isEnabled()).thenReturn(true);
        when(userModel.hasRole(radiusRole)).thenReturn(true);
        when(configuration
                .getRadiusSettings(realmModel))
                .thenReturn(ModelBuilder
                        .createRadiusServerSettings());
        when(configuration.isUsedRadius(realmModel)).thenReturn(true);
        when(configuration.getCommonSettings(realmModel))
                .thenReturn(ModelBuilder.getRadiusCommonSettings());
        when(userCredentialManager
                .getStoredCredentialsByType(realmModel, userModel,
                        RadiusCredentialModel.TYPE))
                .thenReturn(Collections
                        .singletonList(ModelBuilder.createCredentialModel()));
        when(userSessionProvider.getUserSessions(realmModel, userModel))
                .thenReturn(Arrays.asList(userSessionModel));
        when(userSessionModel.getNote(RadiusPasswordMapper.RADIUS_SESSION_PASSWORD))
                .thenReturn("123");
        when(userSessionModel.getRealm()).thenReturn(realmModel);
        when(userSessionModel.getUser()).thenReturn(userModel);
        when(clientConnection.getRemoteAddr()).thenReturn("111.111.111.112");
        when(authProtocol.getRealm()).thenReturn(realmModel);
        when(authProtocol.verifyPassword(anyString())).thenReturn(true);
        when(authProtocol.isValid(any())).thenReturn(true);
        eventBuilder = new EventBuilder(realmModel, session, clientConnection);
        List<?> objects = resetMock();
        if (objects != null) {
            reset(objects.toArray(new Object[objects.size()]));
        }
        resetStatic();
        initDictionary();
    }

    protected <T extends Provider> T getProvider(Class<T> providerClass) {
        return session.getProvider(providerClass);
    }

    @BeforeClass
    public void beforeRadiusTest() {
        MockitoAnnotations.initMocks(this);
    }

    void initDictionary() {
        try {
            DictionaryParser dictionaryParser = DictionaryParser.newClasspathParser();
            realDictionary = dictionaryParser
                    .parseDictionary("org/tinyradius/dictionary/default_dictionary");
            dictionaryParser
                    .parseDictionary(realDictionary,
                            KeycloakRadiusServer.MIKROTIK);
            dictionaryParser
                    .parseDictionary(realDictionary,
                            KeycloakRadiusServer.MS);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}